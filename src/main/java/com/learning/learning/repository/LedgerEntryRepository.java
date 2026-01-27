package com.learning.learning.repository;

import com.learning.learning.entity.Account;
import com.learning.learning.entity.LedgerEntry;
import com.learning.learning.entity.LedgerTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByTransaction(LedgerTransaction transaction);

    List<LedgerEntry> findByAccount(Account account);

    List<LedgerEntry> findByAccountOrderByCreatedAtDesc(Account account);

    Page<LedgerEntry> findByAccountOrderByCreatedAtDesc(Account account, Pageable pageable);

    @Query("SELECT le FROM LedgerEntry le WHERE le.account.id = :accountId AND le.transaction.transactionDate BETWEEN :startDate AND :endDate ORDER BY le.transaction.transactionDate ASC")
    List<LedgerEntry> findByAccountAndDateRange(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // Sum of debits for an account
    @Query("SELECT COALESCE(SUM(le.amount), 0) FROM LedgerEntry le WHERE le.account.id = :accountId AND le.entryType = 'DEBIT'")
    BigDecimal sumDebitsByAccountId(@Param("accountId") Long accountId);

    // Sum of credits for an account
    @Query("SELECT COALESCE(SUM(le.amount), 0) FROM LedgerEntry le WHERE le.account.id = :accountId AND le.entryType = 'CREDIT'")
    BigDecimal sumCreditsByAccountId(@Param("accountId") Long accountId);

    // Calculate account balance (for ASSET/EXPENSE: debits - credits, for LIABILITY/REVENUE/EQUITY: credits - debits)
    @Query("SELECT COALESCE(SUM(CASE WHEN le.entryType = 'DEBIT' THEN le.amount ELSE -le.amount END), 0) FROM LedgerEntry le WHERE le.account.id = :accountId")
    BigDecimal calculateDebitBalanceByAccountId(@Param("accountId") Long accountId);

    @Query("SELECT COALESCE(SUM(CASE WHEN le.entryType = 'CREDIT' THEN le.amount ELSE -le.amount END), 0) FROM LedgerEntry le WHERE le.account.id = :accountId")
    BigDecimal calculateCreditBalanceByAccountId(@Param("accountId") Long accountId);

    // Get last entry for an account (for running balance)
    @Query("SELECT le FROM LedgerEntry le WHERE le.account.id = :accountId ORDER BY le.createdAt DESC LIMIT 1")
    LedgerEntry findLastEntryByAccountId(@Param("accountId") Long accountId);

    // Count entries per account
    long countByAccount(Account account);

    // Sum all debits (for trial balance)
    @Query("SELECT COALESCE(SUM(le.amount), 0) FROM LedgerEntry le WHERE le.entryType = 'DEBIT'")
    BigDecimal sumAllDebits();

    // Sum all credits (for trial balance)
    @Query("SELECT COALESCE(SUM(le.amount), 0) FROM LedgerEntry le WHERE le.entryType = 'CREDIT'")
    BigDecimal sumAllCredits();
}
