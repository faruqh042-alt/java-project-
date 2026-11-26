public class AdoptionRequest {
    private String requestId;
    private String adopterId;
    private String petId;
    private String reason;
    private String requestDate;
    private String status;

    public AdoptionRequest(String requestId, String adopterId, String petId,
                           String reason, String requestDate, String status) {
        this.requestId = requestId;
        this.adopterId = adopterId;
        this.petId = petId;
        this.reason = reason;
        this.requestDate = requestDate;
        this.status = status;
    }

    public String getRequestId() { return requestId; }
    public String getAdopterId() { return adopterId; }
    public String getPetId() { return petId; }
    public String getReason() { return reason; }
    public String getRequestDate() { return requestDate; }
    public String getStatus() { return status; }

    public void approve() { status = "Approved"; }
    public void reject() { status = "Rejected"; }
}
