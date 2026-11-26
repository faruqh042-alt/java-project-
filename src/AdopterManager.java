import java.util.*;
import java.util.stream.Collectors;

public class AdopterManager {
    private final List<Adopter> adopters = new ArrayList<>();
    private final FileManager fileManager;

    public AdopterManager(FileManager fileManager) { this.fileManager = fileManager; }
    public void load() { adopters.clear(); adopters.addAll(fileManager.loadAdopters()); }
    public void save() { fileManager.saveAdopters(adopters); }
    public void addAdopter(Adopter adopter) {
        if (findById(adopter.getAdopterId()) != null) throw new IllegalArgumentException("Duplicate adopter ID");
        adopters.add(adopter);
        save();
    }
    public void updateAdopter(Adopter adopter) {
        if (findById(adopter.getAdopterId()) == null) throw new IllegalArgumentException("Adopter not found");
        save();
    }
    public void deleteAdopter(String adopterId) {
        if (!adopters.removeIf(a -> a.getAdopterId().equals(adopterId))) {
            throw new IllegalArgumentException("Adopter not found");
        }
        save();
    }
    public List<Adopter> searchAdopter(String term) {
        String needle = term == null ? "" : term.trim().toLowerCase();
        if (needle.isEmpty()) return getAllAdopters();
        return adopters.stream().filter(a -> (a.getAdopterId() + " " + a.getName()
                + " " + a.getPhone() + " " + a.getEmail()).toLowerCase().contains(needle))
                .collect(Collectors.toList());
    }
    public Adopter findById(String adopterId) {
        return adopters.stream().filter(a -> a.getAdopterId().equals(adopterId)).findFirst().orElse(null);
    }
    public List<Adopter> getAllAdopters() { return new ArrayList<>(adopters); }
}
