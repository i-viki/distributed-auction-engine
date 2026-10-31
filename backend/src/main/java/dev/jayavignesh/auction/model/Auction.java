package dev.jayavignesh.auction.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Auction {

    private long id;
    private long sellerId;
    private String title;
    private String description;
    private String imageUrl;

    private BigDecimal startingPrice;
    private BigDecimal currentHighestBid;
    private Long currentHighestBidderId;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private String status;
    private long version;

    public Auction() {
    }

    public Auction(
            long id,
            long sellerId,
            String title,
            String description,
            String imageUrl,
            BigDecimal startingPrice,
            BigDecimal currentHighestBid,
            Long currentHighestBidderId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String status,
            long version) {

        this.id = id;
        this.sellerId = sellerId;
        this.title = title;
        this.description = description;
        this.imageUrl = imageUrl;
        this.startingPrice = startingPrice;
        this.currentHighestBid = currentHighestBid;
        this.currentHighestBidderId = currentHighestBidderId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.version = version;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getSellerId() {
        return sellerId;
    }

    public void setSellerId(long sellerId) {
        this.sellerId = sellerId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public BigDecimal getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(
            BigDecimal startingPrice) {

        this.startingPrice = startingPrice;
    }

    public BigDecimal getCurrentHighestBid() {
        return currentHighestBid;
    }

    public void setCurrentHighestBid(
            BigDecimal currentHighestBid) {

        this.currentHighestBid = currentHighestBid;
    }

    public Long getCurrentHighestBidderId() {
        return currentHighestBidderId;
    }

    public void setCurrentHighestBidderId(
            Long currentHighestBidderId) {

        this.currentHighestBidderId =
                currentHighestBidderId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(
            LocalDateTime startTime) {

        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(
            LocalDateTime endTime) {

        this.endTime = endTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "Auction{" +
                "id=" + id +
                ", sellerId=" + sellerId +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", startingPrice=" + startingPrice +
                ", currentHighestBid=" +
                currentHighestBid +
                ", currentHighestBidderId=" +
                currentHighestBidderId +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", status='" + status + '\'' +
                ", version=" + version +
                '}';
    }
}