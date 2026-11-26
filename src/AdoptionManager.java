import java.util.*;

public class AdoptionManager {
    private final List<AdoptionRequest> requests = new ArrayList<>();
    private final List<AdoptionRecord> records = new ArrayList<>();
    private final FileManager fileManager;
    private final PetManager petManager;

    public AdoptionManager(FileManager fileManager, PetManager petManager) {
        this.fileManager = fileManager;
        this.petManager = petManager;
    }

    public void load() {
        requests.clear();
        requests.addAll(fileManager.loadRequests());
        records.clear();
        records.addAll(fileManager.loadAdoptionRecords());
    }

    public void submitRequest(AdoptionRequest request) {
        Pet pet = petManager.findById(request.getPetId());
        if (pet == null) throw new IllegalArgumentException("Selected pet does not exist");
        if (!"Available".equalsIgnoreCase(pet.getAdoptionStatus())) {
            throw new IllegalArgumentException("This pet is no longer available");
        }
        for (AdoptionRequest existing : requests) {
            if (existing.getPetId().equals(request.getPetId())
                    && ("Pending".equalsIgnoreCase(existing.getStatus())
                    || "Approved".equalsIgnoreCase(existing.getStatus()))) {
                throw new IllegalArgumentException("This pet already has a pending or approved request");
            }
        }
        requests.add(request);
        fileManager.saveRequests(requests);
    }

    public boolean approveRequest(String requestId) {
        AdoptionRequest request = findRequest(requestId);
        if (request == null) throw new IllegalArgumentException("Request not found");
        if (!"Pending".equalsIgnoreCase(request.getStatus())) {
            throw new IllegalArgumentException("Only pending requests can be approved");
        }
        Pet pet = petManager.findById(request.getPetId());
        if (pet == null || !"Available".equalsIgnoreCase(pet.getAdoptionStatus())) {
            throw new IllegalArgumentException("The selected pet is not available");
        }
        for (AdoptionRecord record : records) {
            if (record.getPetId().equals(request.getPetId())) {
                throw new IllegalArgumentException("An adoption record already exists for this pet");
            }
        }

        request.approve();
        pet.setAdoptionStatus("Adopted");
        String adoptionId = nextAdoptionId();
        String date = java.time.LocalDate.now().toString();
        records.add(new AdoptionRecord(adoptionId, pet.getPetId(), request.getAdopterId(), date, "Completed"));
        petManager.save();
        fileManager.saveRequests(requests);
        fileManager.saveAdoptionRecords(records);
        return true;
    }

    public boolean rejectRequest(String requestId) {
        AdoptionRequest request = findRequest(requestId);
        if (request == null) throw new IllegalArgumentException("Request not found");
        if (!"Pending".equalsIgnoreCase(request.getStatus())) {
            throw new IllegalArgumentException("Only pending requests can be rejected");
        }
        request.reject();
        fileManager.saveRequests(requests);
        return true;
    }

    public AdoptionRequest findRequest(String requestId) {
        return requests.stream().filter(r -> r.getRequestId().equals(requestId))
                .findFirst().orElse(null);
    }

    public List<AdoptionRequest> getAllRequests() { return new ArrayList<>(requests); }
    public List<AdoptionRecord> getAdoptionRecords() { return new ArrayList<>(records); }

    private String nextAdoptionId() {
        int max = 0;
        for (AdoptionRecord record : records) {
            try {
                max = Math.max(max, Integer.parseInt(record.getAdoptionId().replaceAll("[^0-9]", "")));
            } catch (RuntimeException ignored) {}
        }
        return "AD" + String.format("%03d", max + 1);
    }
}
