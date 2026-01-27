package com.learning.learning.controller;

import com.learning.learning.entity.Account;
import com.learning.learning.entity.LedgerEntry;
import com.learning.learning.entity.LedgerTransaction;
import com.learning.learning.repository.AccountRepository;
import com.learning.learning.repository.LedgerEntryRepository;
import com.learning.learning.repository.LedgerTransactionRepository;
import com.learning.learning.service.LedgerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/admin/ledger")
public class LedgerController {

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerTransactionRepository transactionRepository;

    @Autowired
    private LedgerEntryRepository entryRepository;

    /**
     * Ledger dashboard - overview of accounts and balances.
     */
    @GetMapping
    public String ledgerDashboard(Model model) {
        List<Account> accounts = ledgerService.getAllAccounts();

        // Calculate totals by type
        BigDecimal totalAssets = accounts.stream()
                .filter(a -> a.getAccountType() == Account.AccountType.ASSET)
                .map(Account::getCurrentBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalLiabilities = accounts.stream()
                .filter(a -> a.getAccountType() == Account.AccountType.LIABILITY)
                .map(Account::getCurrentBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRevenue = accounts.stream()
                .filter(a -> a.getAccountType() == Account.AccountType.REVENUE)
                .map(Account::getCurrentBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalExpenses = accounts.stream()
                .filter(a -> a.getAccountType() == Account.AccountType.EXPENSE)
                .map(Account::getCurrentBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Trial balance check
        BigDecimal[] trialBalance = ledgerService.getTrialBalanceTotals();
        boolean isBalanced = ledgerService.verifyTrialBalance();

        // Recent transactions
        Page<LedgerTransaction> recentTransactions = transactionRepository
                .findAllByOrderByTransactionDateDesc(PageRequest.of(0, 10));

        model.addAttribute("accounts", accounts);
        model.addAttribute("totalAssets", totalAssets);
        model.addAttribute("totalLiabilities", totalLiabilities);
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("totalExpenses", totalExpenses);
        model.addAttribute("trialBalanceDebits", trialBalance[0]);
        model.addAttribute("trialBalanceCredits", trialBalance[1]);
        model.addAttribute("isBalanced", isBalanced);
        model.addAttribute("recentTransactions", recentTransactions.getContent());

        return "admin/ledger/dashboard";
    }

    /**
     * Chart of Accounts view.
     */
    @GetMapping("/accounts")
    public String chartOfAccounts(Model model) {
        List<Account> accounts = accountRepository.findByIsActiveTrueOrderByAccountCodeAsc();
        model.addAttribute("accounts", accounts);
        return "admin/ledger/accounts";
    }

    /**
     * Account detail - show transaction history for an account.
     */
    @GetMapping("/accounts/{id}")
    public String accountDetail(@PathVariable Long id, Model model) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        List<LedgerEntry> entries = entryRepository.findByAccountOrderByCreatedAtDesc(account);
        BigDecimal balance = ledgerService.getAccountBalance(account);

        model.addAttribute("account", account);
        model.addAttribute("entries", entries);
        model.addAttribute("balance", balance);

        return "admin/ledger/account-detail";
    }

    /**
     * All transactions view.
     */
    @GetMapping("/transactions")
    public String allTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        Page<LedgerTransaction> transactions = transactionRepository
                .findAllByOrderByTransactionDateDesc(PageRequest.of(page, size));

        model.addAttribute("transactions", transactions);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", transactions.getTotalPages());

        return "admin/ledger/transactions";
    }

    /**
     * Transaction detail - show all entries for a transaction.
     */
    @GetMapping("/transactions/{id}")
    public String transactionDetail(@PathVariable Long id, Model model) {
        LedgerTransaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        List<LedgerEntry> entries = entryRepository.findByTransaction(transaction);

        model.addAttribute("transaction", transaction);
        model.addAttribute("entries", entries);

        return "admin/ledger/transaction-detail";
    }

    /**
     * Initialize system accounts (one-time setup).
     */
    @PostMapping("/initialize")
    public String initializeAccounts() {
        ledgerService.initializeSystemAccounts();
        return "redirect:/admin/ledger?initialized=true";
    }
}
