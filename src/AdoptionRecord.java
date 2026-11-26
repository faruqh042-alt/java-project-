public class AdoptionRecord {
    private String adoptionId;
    private String petId;
    private String adopterId;
    private String adoptionDate;
    private String status;

    public AdoptionRecord(String adoptionId, String petId, String adopterId,
                          String adoptionDate, String status) {
        this.adoptionId = adoptionId;
        this.petId = petId;
        this.adopterId = adopterId;
        this.adoptionDate = adoptionDate;
        this.status = status;
    }

    public String getAdoptionId() { return adoptionId; }
    public String getPetId() { return petId; }
    public String getAdopterId() { return adopterId; }
    public String getAdoptionDate() { return adoptionDate; }
    public String getStatus() { return status; }

    public String display() {
        return adoptionId + " - Pet " + petId + " adopted by " + adopterId;
    }
}
