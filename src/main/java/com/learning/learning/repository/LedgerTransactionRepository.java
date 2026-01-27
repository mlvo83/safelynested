package com.learning.learning.repository;

import com.learning.learning.entity.Charity;
import com.learning.learning.entity.LedgerTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long> {

    Optional<LedgerTransaction> findByTransactionCode(String transactionCode);

    List<LedgerTransaction> findByTransactionType(LedgerTransaction.TransactionType transactionType);

    List<LedgerTransaction> findByCharity(Charity charity);

    List<LedgerTransaction> findByReferenceTypeAndReferenceId(String referenceType, Long referenceId);

    @Query("SELECT lt FROM LedgerTransaction lt WHERE lt.transactionDate BETWEEN :startDate AND :endDate ORDER BY lt.transactionDate DESC")
    List<LedgerTransaction> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT lt FROM LedgerTransaction lt WHERE lt.charity.id = :charityId AND lt.transactionDate BETWEEN :startDate AND :endDate ORDER BY lt.transactionDate DESC")
    List<LedgerTransaction> findByCharityAndDateRange(
            @Param("charityId") Long charityId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    Page<LedgerTransaction> findAllByOrderByTransactionDateDesc(Pageable pageable);

    Page<LedgerTransaction> findByCharityOrderByTransactionDateDesc(Charity charity, Pageable pageable);

    @Query("SELECT MAX(lt.transactionCode) FROM LedgerTransaction lt WHERE lt.transactionCode LIKE :prefix%")
    String findMaxTransactionCodeByPrefix(@Param("prefix") String prefix);

    @Query("SELECT lt FROM LedgerTransaction lt WHERE lt.isReversed = false ORDER BY lt.transactionDate DESC")
    List<LedgerTransaction> findActiveTransactions();

    long countByTransactionType(LedgerTransaction.TransactionType transactionType);
}
