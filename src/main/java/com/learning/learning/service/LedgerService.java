package com.learning.learning.service;

import com.learning.learning.entity.*;
import com.learning.learning.repository.AccountRepository;
import com.learning.learning.repository.LedgerEntryRepository;
import com.learning.learning.repository.LedgerTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * LedgerService - Core service for managing the double-entry ledger.
 * Handles recording transactions, calculating balances, and generating reports.
 */
@Service
public class LedgerService {

    // System account codes
    public static final String CASH_ACCOUNT = "1000";
    public static final String ACCOUNTS_RECEIVABLE = "1100";
    public static final String FUNDS_HELD_PREFIX = "2000";
    public static final String ALLOCATED_FUNDS = "2100";
    public static final String PLATFORM_FEE_REVENUE = "4000";
    public static final String FACILITATOR_FEE_REVENUE = "4100";
    public static final String HOUSING_DISBURSEMENTS = "5000";
    public static final String REFUNDS_EXPENSE = "5100";

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerTransactionRepository transactionRepository;

    @Autowired
    private LedgerEntryRepository entryRepository;

    // ========================================
    // ACCOUNT MANAGEMENT
    // ========================================

    /**
     * Initialize system accounts if they don't exist.
     * Should be called on application startup.
     */
    @Transactional
    public void initializeSystemAccounts() {
        createSystemAccountIfNotExists(CASH_ACCOUNT, "Cash / Bank", Account.AccountType.ASSET);
        createSystemAccountIfNotExists(ACCOUNTS_RECEIVABLE, "Accounts Receivable", Account.AccountType.ASSET);
        createSystemAccountIfNotExists(FUNDS_HELD_PREFIX, "Funds Held for Charities", Account.AccountType.LIABILITY);
        createSystemAccountIfNotExists(ALLOCATED_FUNDS, "Funds Allocated to Situations", Account.AccountType.LIABILITY);
        createSystemAccountIfNotExists(PLATFORM_FEE_REVENUE, "Platform Fee Revenue (7%)", Account.AccountType.REVENUE);
        createSystemAccountIfNotExists(FACILITATOR_FEE_REVENUE, "Facilitator Fee Revenue (3%)", Account.AccountType.REVENUE);
        createSystemAccountIfNotExists(HOUSING_DISBURSEMENTS, "Housing Disbursements", Account.AccountType.EXPENSE);
        createSystemAccountIfNotExists(REFUNDS_EXPENSE, "Refunds Issued", Account.AccountType.EXPENSE);
    }

    private void createSystemAccountIfNotExists(String code, String name, Account.AccountType type) {
        if (!accountRepository.existsByAccountCode(code)) {
            Account account = new Account();
            account.setAccountCode(code);
            account.setAccountName(name);
            account.setAccountType(type);
            account.setIsSystemAccount(true);
            account.setIsActive(true);
            accountRepository.save(account);
        }
    }

    /**
     * Create a charity-specific fund account.
     */
    @Transactional
    public Account createCharityFundAccount(Charity charity) {
        String accountCode = FUNDS_HELD_PREFIX + "-" + charity.getId();

        return accountRepository.findByAccountCode(accountCode)
                .orElseGet(() -> {
                    Account account = new Account();
                    account.setAccountCode(accountCode);
                    account.setAccountName("Charity Fund: " + charity.getCharityName());
                    account.setAccountType(Account.AccountType.LIABILITY);
                    account.setCharity(charity);
                    account.setIsSystemAccount(false);
                    account.setIsActive(true);

                    // Set parent account
                    accountRepository.findByAccountCode(FUNDS_HELD_PREFIX)
                            .ifPresent(account::setParentAccount);

                    return accountRepository.save(account);
                });
    }

    /**
     * Get or create charity fund account.
     */
    public Account getCharityFundAccount(Charity charity) {
        String accountCode = FUNDS_HELD_PREFIX + "-" + charity.getId();
        return accountRepository.findByAccountCode(accountCode)
                .orElseGet(() -> createCharityFundAccount(charity));
    }

    // ========================================
    // TRANSACTION RECORDING
    // ========================================

    /**
     * Record a donation received.
     * Creates entries for cash received, charity fund credit, and fee revenues.
     */
    @Transactional
    public LedgerTransaction recordDonation(Donation donation, User recordedBy) {
        Charity charity = donation.getCharity();
        Account cashAccount = getAccountByCode(CASH_ACCOUNT);
        Account charityFundAccount = getCharityFundAccount(charity);
        Account platformFeeAccount = getAccountByCode(PLATFORM_FEE_REVENUE);
        Account facilitatorFeeAccount = getAccountByCode(FACILITATOR_FEE_REVENUE);

        // Create transaction
        LedgerTransaction transaction = new LedgerTransaction();
        transaction.setTransactionCode(generateTransactionCode());
        transaction.setTransactionType(LedgerTransaction.TransactionType.DONATION_RECEIVED);
        transaction.setTransactionDate(donation.getDonatedAt() != null ? donation.getDonatedAt() : LocalDateTime.now());
        transaction.setDescription("Donation received from " + donation.getDonor().getDonorName());
        transaction.setReferenceType("DONATION");
        transaction.setReferenceId(donation.getId());
        transaction.setCharity(charity);
        transaction.setCreatedBy(recordedBy);
        transaction.setTotalAmount(donation.getGrossAmount());

        // DEBIT Cash (Asset increases)
        LedgerEntry cashDebit = LedgerEntry.debit(cashAccount, donation.getGrossAmount())
                .withMemo("Donation #" + donation.getId());
        transaction.addEntry(cashDebit);

        // CREDIT Charity Fund (Liability increases) - Net amount
        LedgerEntry fundCredit = LedgerEntry.credit(charityFundAccount, donation.getNetAmount())
                .withMemo("Net donation for housing");
        transaction.addEntry(fundCredit);

        // CREDIT Platform Fee Revenue
        if (donation.getPlatformFee() != null && donation.getPlatformFee().compareTo(BigDecimal.ZERO) > 0) {
            LedgerEntry platformFeeCredit = LedgerEntry.credit(platformFeeAccount, donation.getPlatformFee())
                    .withMemo("Platform fee (7%)");
            transaction.addEntry(platformFeeCredit);
        }

        // CREDIT Facilitator Fee Revenue
        if (donation.getFacilitatorFee() != null && donation.getFacilitatorFee().compareTo(BigDecimal.ZERO) > 0) {
            LedgerEntry facilitatorFeeCredit = LedgerEntry.credit(facilitatorFeeAccount, donation.getFacilitatorFee())
                    .withMemo("Facilitator fee (3%)");
            transaction.addEntry(facilitatorFeeCredit);
        }

        // Validate balance
        if (!transaction.isBalanced()) {
            throw new IllegalStateException("Transaction is not balanced! Debits: " +
                    transaction.getTotalDebits() + ", Credits: " + transaction.getTotalCredits());
        }

        // Save transaction
        transaction = transactionRepository.save(transaction);

        // Update running balances
        updateAccountBalances(transaction);

        return transaction;
    }

    /**
     * Record fund allocation to a situation.
     */
    @Transactional
    public LedgerTransaction recordAllocation(SituationFunding funding, User allocatedBy) {
        Charity charity = funding.getSituation().getCharity();
        Account charityFundAccount = getCharityFundAccount(charity);
        Account allocatedFundsAccount = getAccountByCode(ALLOCATED_FUNDS);

        BigDecimal amount = funding.getAmountAllocated();

        LedgerTransaction transaction = new LedgerTransaction();
        transaction.setTransactionCode(generateTransactionCode());
        transaction.setTransactionType(LedgerTransaction.TransactionType.FUND_ALLOCATED);
        transaction.setTransactionDate(LocalDateTime.now());
        transaction.setDescription("Funds allocated to situation: " + funding.getSituation().getDescription());
        transaction.setReferenceType("SITUATION_FUNDING");
        transaction.setReferenceId(funding.getId());
        transaction.setCharity(charity);
        transaction.setCreatedBy(allocatedBy);
        transaction.setTotalAmount(amount);

        // DEBIT Charity Fund (reduce available funds)
        transaction.addEntry(LedgerEntry.debit(charityFundAccount, amount)
                .withMemo("Allocated to situation #" + funding.getSituation().getId()));

        // CREDIT Allocated Funds (increase committed funds)
        transaction.addEntry(LedgerEntry.credit(allocatedFundsAccount, amount)
                .withMemo("Committed for housing"));

        if (!transaction.isBalanced()) {
            throw new IllegalStateException("Transaction is not balanced!");
        }

        transaction = transactionRepository.save(transaction);
        updateAccountBalances(transaction);

        return transaction;
    }

    /**
     * Record disbursement for a booking.
     */
    @Transactional
    public LedgerTransaction recordDisbursement(Booking booking, BigDecimal amount, User disbursedBy) {
        Account allocatedFundsAccount = getAccountByCode(ALLOCATED_FUNDS);
        Account cashAccount = getAccountByCode(CASH_ACCOUNT);

        LedgerTransaction transaction = new LedgerTransaction();
        transaction.setTransactionCode(generateTransactionCode());
        transaction.setTransactionType(LedgerTransaction.TransactionType.FUND_DISBURSED);
        transaction.setTransactionDate(LocalDateTime.now());
        transaction.setDescription("Disbursement for booking: " + booking.getConfirmationCode());
        transaction.setReferenceType("BOOKING");
        transaction.setReferenceId(booking.getId());
        transaction.setCreatedBy(disbursedBy);
        transaction.setTotalAmount(amount);

        // DEBIT Allocated Funds (reduce committed funds)
        transaction.addEntry(LedgerEntry.debit(allocatedFundsAccount, amount)
                .withMemo("Released for booking #" + booking.getId()));

        // CREDIT Cash (reduce cash - payment going out)
        transaction.addEntry(LedgerEntry.credit(cashAccount, amount)
                .withMemo("Payment to " + booking.getLocationName()));

        if (!transaction.isBalanced()) {
            throw new IllegalStateException("Transaction is not balanced!");
        }

        transaction = transactionRepository.save(transaction);
        updateAccountBalances(transaction);

        return transaction;
    }

    /**
     * Record a donation refund.
     */
    @Transactional
    public LedgerTransaction recordRefund(Donation donation, String reason, User refundedBy) {
        Charity charity = donation.getCharity();
        Account cashAccount = getAccountByCode(CASH_ACCOUNT);
        Account charityFundAccount = getCharityFundAccount(charity);
        Account platformFeeAccount = getAccountByCode(PLATFORM_FEE_REVENUE);
        Account facilitatorFeeAccount = getAccountByCode(FACILITATOR_FEE_REVENUE);

        LedgerTransaction transaction = new LedgerTransaction();
        transaction.setTransactionCode(generateTransactionCode());
        transaction.setTransactionType(LedgerTransaction.TransactionType.DONATION_REFUND);
        transaction.setTransactionDate(LocalDateTime.now());
        transaction.setDescription("Refund for donation #" + donation.getId() + ": " + reason);
        transaction.setReferenceType("DONATION");
        transaction.setReferenceId(donation.getId());
        transaction.setCharity(charity);
        transaction.setCreatedBy(refundedBy);
        transaction.setTotalAmount(donation.getGrossAmount());
        transaction.setNotes(reason);

        // Reverse the original entries
        // DEBIT Charity Fund (reduce liability)
        transaction.addEntry(LedgerEntry.debit(charityFundAccount, donation.getNetAmount())
                .withMemo("Refund - net amount"));

        // DEBIT Platform Fee (reduce revenue)
        if (donation.getPlatformFee() != null && donation.getPlatformFee().compareTo(BigDecimal.ZERO) > 0) {
            transaction.addEntry(LedgerEntry.debit(platformFeeAccount, donation.getPlatformFee())
                    .withMemo("Refund - platform fee"));
        }

        // DEBIT Facilitator Fee (reduce revenue)
        if (donation.getFacilitatorFee() != null && donation.getFacilitatorFee().compareTo(BigDecimal.ZERO) > 0) {
            transaction.addEntry(LedgerEntry.debit(facilitatorFeeAccount, donation.getFacilitatorFee())
                    .withMemo("Refund - facilitator fee"));
        }

        // CREDIT Cash (money going out)
        transaction.addEntry(LedgerEntry.credit(cashAccount, donation.getGrossAmount())
                .withMemo("Refund payment"));

        if (!transaction.isBalanced()) {
            throw new IllegalStateException("Transaction is not balanced!");
        }

        transaction = transactionRepository.save(transaction);
        updateAccountBalances(transaction);

        return transaction;
    }

    // ========================================
    // BALANCE CALCULATIONS
    // ========================================

    /**
     * Get current balance for an account.
     */
    public BigDecimal getAccountBalance(Account account) {
        BigDecimal debits = entryRepository.sumDebitsByAccountId(account.getId());
        BigDecimal credits = entryRepository.sumCreditsByAccountId(account.getId());

        // For ASSET and EXPENSE accounts: balance = debits - credits
        // For LIABILITY, EQUITY, REVENUE accounts: balance = credits - debits
        if (account.isAssetOrExpense()) {
            return debits.subtract(credits);
        } else {
            return credits.subtract(debits);
        }
    }

    /**
     * Get available funds for a charity.
     */
    public BigDecimal getCharityAvailableFunds(Charity charity) {
        Account charityFundAccount = getCharityFundAccount(charity);
        return getAccountBalance(charityFundAccount);
    }

    /**
     * Update account balances after a transaction.
     */
    private void updateAccountBalances(LedgerTransaction transaction) {
        for (LedgerEntry entry : transaction.getEntries()) {
            Account account = entry.getAccount();
            BigDecimal newBalance = getAccountBalance(account);
            account.setCurrentBalance(newBalance);
            entry.setRunningBalance(newBalance);
            accountRepository.save(account);
            entryRepository.save(entry);
        }
    }

    // ========================================
    // QUERIES
    // ========================================

    /**
     * Get account history.
     */
    public List<LedgerEntry> getAccountHistory(Account account, LocalDate from, LocalDate to) {
        LocalDateTime startDateTime = from.atStartOfDay();
        LocalDateTime endDateTime = to.plusDays(1).atStartOfDay();
        return entryRepository.findByAccountAndDateRange(account.getId(), startDateTime, endDateTime);
    }

    /**
     * Get transactions by reference.
     */
    public List<LedgerTransaction> getTransactionsByReference(String referenceType, Long referenceId) {
        return transactionRepository.findByReferenceTypeAndReferenceId(referenceType, referenceId);
    }

    /**
     * Get all accounts.
     */
    public List<Account> getAllAccounts() {
        return accountRepository.findByIsActiveTrueOrderByAccountCodeAsc();
    }

    /**
     * Get account by code.
     */
    public Account getAccountByCode(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new RuntimeException("Account not found: " + code));
    }

    // ========================================
    // UTILITIES
    // ========================================

    /**
     * Generate unique transaction code.
     * Format: TXN-YYYYMMDD-NNNNN
     */
    private String generateTransactionCode() {
        String datePrefix = "TXN-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        String maxCode = transactionRepository.findMaxTransactionCodeByPrefix(datePrefix);

        int nextNumber = 1;
        if (maxCode != null) {
            String numberPart = maxCode.substring(datePrefix.length());
            nextNumber = Integer.parseInt(numberPart) + 1;
        }

        return datePrefix + String.format("%05d", nextNumber);
    }

    /**
     * Verify trial balance (total debits = total credits).
     */
    public boolean verifyTrialBalance() {
        BigDecimal totalDebits = entryRepository.sumAllDebits();
        BigDecimal totalCredits = entryRepository.sumAllCredits();
        return totalDebits.compareTo(totalCredits) == 0;
    }

    /**
     * Get trial balance totals.
     */
    public BigDecimal[] getTrialBalanceTotals() {
        return new BigDecimal[]{
                entryRepository.sumAllDebits(),
                entryRepository.sumAllCredits()
        };
    }
}
