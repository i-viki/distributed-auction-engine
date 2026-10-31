package dev.jayavignesh.auction.service;

import dev.jayavignesh.auction.dao.AuctionDAO;
import dev.jayavignesh.auction.dto.CreateAuctionRequest;
import dev.jayavignesh.auction.exception.BusinessValidationException;
import dev.jayavignesh.auction.exception.ResourceNotFoundException;
import dev.jayavignesh.auction.model.Auction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class AuctionService {

    private static final Set<String> VALID_STATUSES = Set.of("SCHEDULED", "LIVE", "CLOSED");

    private final AuctionDAO auctionDAO;

    public AuctionService(
            AuctionDAO auctionDAO) {

        this.auctionDAO = auctionDAO;
    }

    public List<Auction> getAllAuctions(
            String status,
            String search,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            boolean endingSoon,
            int page,
            int size) {

        if (status != null) {
            status = status.trim().toUpperCase();
            if (!VALID_STATUSES.contains(status)) {
                throw new BusinessValidationException(
                        "Invalid auction status. Allowed values: SCHEDULED, LIVE, CLOSED.");
            }
        }

        if (search != null) {
            search = search.trim();
            if (search.isEmpty()) {
                search = null;
            }
        }

        if (minPrice != null && minPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessValidationException("Minimum price cannot be negative.");
        }

        if (maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessValidationException("Maximum price cannot be negative.");
        }

        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BusinessValidationException("Minimum price cannot be greater than maximum price.");
        }

        if (endingSoon) {
            status = "LIVE";
        }

        return auctionDAO.findAll(
                status,
                search,
                minPrice,
                maxPrice,
                endingSoon,
                page,
                size);
    }

    public Auction getAuctionById(long auctionId) {

        if (auctionId <= 0) {
            throw new BusinessValidationException("Auction ID must be greater than zero.");
        }

        Auction auction = auctionDAO.findById(auctionId);

        if (auction == null) {
            throw new ResourceNotFoundException("Auction not found with ID: " + auctionId);
        }

        return auction;
    }

    public Auction createAuction(
            long sellerId,
            CreateAuctionRequest request) {

        if (sellerId <= 0) {
            throw new BusinessValidationException("Seller ID must be greater than zero.");
        }

        LocalDateTime now = LocalDateTime.now();

        LocalDateTime effectiveStartTime = request.getStartTime();
        LocalDateTime effectiveEndTime = request.getEndTime();

        // 1. Resolve start time:
        // If omitted or in the past (e.g. immediate auction or client clock drift),
        // default/clamp to server now
        if (effectiveStartTime == null || effectiveStartTime.isBefore(now)) {
            effectiveStartTime = now;
        }

        // 2. Resolve end time:
        // If durationMinutes is provided, calculate end time server-side from
        // effectiveStartTime
        if (request.getDurationMinutes() != null && request.getDurationMinutes() > 0) {
            effectiveEndTime = effectiveStartTime.plusMinutes(request.getDurationMinutes());
        } else if (effectiveEndTime == null) {
            // Default auction duration: 10 minutes if neither durationMinutes nor endTime
            // is provided
            effectiveEndTime = effectiveStartTime.plusMinutes(10);
        }

        if (!effectiveEndTime.isAfter(now)) {
            throw new BusinessValidationException("Auction end time must be in the future.");
        }

        if (!effectiveEndTime.isAfter(effectiveStartTime)) {
            throw new BusinessValidationException("Auction end time must be after start time.");
        }

        Auction auction = new Auction();
        auction.setSellerId(sellerId);
        auction.setTitle(request.getTitle().trim());
        auction.setDescription(request.getDescription().trim());
        auction.setImageUrl(request.getImageUrl());
        auction.setStartingPrice(request.getStartingPrice());
        auction.setCurrentHighestBid(request.getStartingPrice());
        auction.setCurrentHighestBidderId(null);
        auction.setStartTime(effectiveStartTime);
        auction.setEndTime(effectiveEndTime);

        // If start time has already arrived (or is within 5-minute tolerance), activate
        // immediately as LIVE
        if (!effectiveStartTime.isAfter(now.plusMinutes(5))) {
            auction.setStatus("LIVE");
        } else {
            auction.setStatus("SCHEDULED");
        }

        auction.setVersion(0);

        return auctionDAO.save(auction);
    }
}
