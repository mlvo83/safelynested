package com.learning.learning.service;

import com.learning.learning.entity.Booking;
import com.learning.learning.entity.Donation;
import com.learning.learning.repository.BookingRepository;
import com.learning.learning.repository.DonationRepository;
import com.learning.learning.repository.SituationFundingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service for aggregating donor dashboard statistics
 */
@Service
public class DonorDashboardService {

    @Autowired
    private DonationRepository donationRepository;

    @Autowired
    private SituationFundingRepository situationFundingRepository;

    @Autowired
    private BookingRepository bookingRepository;

    /**
     * Get comprehensive dashboard statistics for a donor
     */
    public DonorDashboardStats getDashboardStats(Long donorId) {
        List<Donation> donations = donationRepository.findByDonorId(donorId);

        // Calculate totals from donations
        BigDecimal totalDonated = BigDecimal.ZERO;
        BigDecimal totalPlatformFees = BigDecimal.ZERO;
        BigDecimal totalFacilitatorFees = BigDecimal.ZERO;
        BigDecimal totalProcessingFees = BigDecimal.ZERO;
        BigDecimal totalNetFunds = BigDecimal.ZERO;
        int totalNightsFunded = 0;

        for (Donation d : donations) {
            if (d.getGrossAmount() != null) {
                totalDonated = totalDonated.add(d.getGrossAmount());
            }
            if (d.getPlatformFee() != null) {
                totalPlatformFees = totalPlatformFees.add(d.getPlatformFee());
            }
            if (d.getFacilitatorFee() != null) {
                totalFacilitatorFees = totalFacilitatorFees.add(d.getFacilitatorFee());
            }
            if (d.getProcessingFee() != null) {
                totalProcessingFees = totalProcessingFees.add(d.getProcessingFee());
            }
            if (d.getNetAmount() != null) {
                totalNetFunds = totalNetFunds.add(d.getNetAmount());
            }
            if (d.getNightsFunded() != null) {
                totalNightsFunded += d.getNightsFunded();
            }
        }

        BigDecimal totalFees = totalPlatformFees.add(totalFacilitatorFees).add(totalProcessingFees);

        // Get nights used from situation fundings
        Integer nightsUsed = situationFundingRepository.sumNightsUsedByDonorId(donorId);
        if (nightsUsed == null) nightsUsed = 0;

        int nightsRemaining = totalNightsFunded - nightsUsed;
        if (nightsRemaining < 0) nightsRemaining = 0;

        // Compute money-based stats from funded bookings
        BigDecimal totalAmountUsed = BigDecimal.ZERO;
        int staysFunded = 0;
        for (Donation d : donations) {
            BigDecimal used = bookingRepository.sumFundedAmountByDonationId(d.getId());
            if (used != null) {
                totalAmountUsed = totalAmountUsed.add(used);
            }
            Long bookingCount = bookingRepository.countByFundingDonationIdAndBookingStatusNot(
                    d.getId(), Booking.BookingStatus.CANCELLED);
            if (bookingCount != null) {
                staysFunded += bookingCount.intValue();
            }
        }
        BigDecimal amountRemaining = totalNetFunds.subtract(totalAmountUsed);
        if (amountRemaining.compareTo(BigDecimal.ZERO) < 0) {
            amountRemaining = BigDecimal.ZERO;
        }

        // Count donations by status
        long pendingCount = donations.stream()
                .filter(d -> d.getStatus() == Donation.DonationStatus.PENDING)
                .count();
        long verifiedCount = donations.stream()
                .filter(d -> d.getStatus() == Donation.DonationStatus.VERIFIED ||
                        d.getStatus() == Donation.DonationStatus.ALLOCATED ||
                        d.getStatus() == Donation.DonationStatus.PARTIALLY_USED ||
                        d.getStatus() == Donation.DonationStatus.FULLY_USED)
                .count();

        return new DonorDashboardStats(
                totalDonated,
                totalFees,
                totalPlatformFees,
                totalFacilitatorFees,
                totalProcessingFees,
                totalNetFunds,
                totalNightsFunded,
                nightsUsed,
                nightsRemaining,
                donations.size(),
                (int) pendingCount,
                (int) verifiedCount,
                totalAmountUsed,
                amountRemaining,
                staysFunded
        );
    }

    /**
     * Get dashboard stats for a donor at a specific charity
     */
    public DonorDashboardStats getDashboardStatsForCharity(Long donorId, Long charityId) {
        List<Donation> donations = donationRepository.findByDonorIdAndCharityId(donorId, charityId);

        BigDecimal totalDonated = BigDecimal.ZERO;
        BigDecimal totalPlatformFees = BigDecimal.ZERO;
        BigDecimal totalFacilitatorFees = BigDecimal.ZERO;
        BigDecimal totalProcessingFees = BigDecimal.ZERO;
        BigDecimal totalNetFunds = BigDecimal.ZERO;
        int totalNightsFunded = 0;

        for (Donation d : donations) {
            if (d.getGrossAmount() != null) {
                totalDonated = totalDonated.add(d.getGrossAmount());
            }
            if (d.getPlatformFee() != null) {
                totalPlatformFees = totalPlatformFees.add(d.getPlatformFee());
            }
            if (d.getFacilitatorFee() != null) {
                totalFacilitatorFees = totalFacilitatorFees.add(d.getFacilitatorFee());
            }
            if (d.getProcessingFee() != null) {
                totalProcessingFees = totalProcessingFees.add(d.getProcessingFee());
            }
            if (d.getNetAmount() != null) {
                totalNetFunds = totalNetFunds.add(d.getNetAmount());
            }
            if (d.getNightsFunded() != null) {
                totalNightsFunded += d.getNightsFunded();
            }
        }

        BigDecimal totalFees = totalPlatformFees.add(totalFacilitatorFees).add(totalProcessingFees);

        // For charity-specific stats, we'd need to filter situation fundings by charity
        // For now, use the donation-level nights funded
        int nightsUsed = 0;
        int nightsRemaining = totalNightsFunded;

        // Compute money-based stats from funded bookings
        BigDecimal totalAmountUsed = BigDecimal.ZERO;
        int staysFunded = 0;
        for (Donation d : donations) {
            BigDecimal used = bookingRepository.sumFundedAmountByDonationId(d.getId());
            if (used != null) {
                totalAmountUsed = totalAmountUsed.add(used);
            }
            Long bookingCount = bookingRepository.countByFundingDonationIdAndBookingStatusNot(
                    d.getId(), Booking.BookingStatus.CANCELLED);
            if (bookingCount != null) {
                staysFunded += bookingCount.intValue();
            }
        }
        BigDecimal amountRemaining = totalNetFunds.subtract(totalAmountUsed);
        if (amountRemaining.compareTo(BigDecimal.ZERO) < 0) {
            amountRemaining = BigDecimal.ZERO;
        }

        long pendingCount = donations.stream()
                .filter(d -> d.getStatus() == Donation.DonationStatus.PENDING)
                .count();
        long verifiedCount = donations.stream()
                .filter(d -> d.getStatus() != Donation.DonationStatus.PENDING &&
                        d.getStatus() != Donation.DonationStatus.CANCELLED)
                .count();

        return new DonorDashboardStats(
                totalDonated,
                totalFees,
                totalPlatformFees,
                totalFacilitatorFees,
                totalProcessingFees,
                totalNetFunds,
                totalNightsFunded,
                nightsUsed,
                nightsRemaining,
                donations.size(),
                (int) pendingCount,
                (int) verifiedCount,
                totalAmountUsed,
                amountRemaining,
                staysFunded
        );
    }

    /**
     * DTO for dashboard statistics
     */
    public record DonorDashboardStats(
            BigDecimal totalDonated,
            BigDecimal totalFees,
            BigDecimal platformFees,
            BigDecimal facilitatorFees,
            BigDecimal processingFees,
            BigDecimal netFunds,
            int totalNightsFunded,
            int nightsUsed,
            int nightsRemaining,
            int donationCount,
            int pendingDonations,
            int verifiedDonations,
            BigDecimal amountUsed,
            BigDecimal amountRemaining,
            int staysFunded
    ) {}
}
