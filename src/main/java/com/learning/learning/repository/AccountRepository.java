package com.learning.learning.repository;

import com.learning.learning.entity.Account;
import com.learning.learning.entity.Charity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountCode(String accountCode);

    List<Account> findByAccountType(Account.AccountType accountType);

    List<Account> findByCharity(Charity charity);

    List<Account> findByIsSystemAccountTrue();

    List<Account> findByIsActiveTrue();

    List<Account> findByIsActiveTrueOrderByAccountCodeAsc();

    @Query("SELECT a FROM Account a WHERE a.charity.id = :charityId AND a.isActive = true")
    List<Account> findActiveAccountsByCharityId(@Param("charityId") Long charityId);

    @Query("SELECT a FROM Account a WHERE a.accountCode LIKE :prefix%")
    List<Account> findByAccountCodeStartingWith(@Param("prefix") String prefix);

    boolean existsByAccountCode(String accountCode);
}
