import java.util.*;
import java.util.stream.Collectors;

public class PetManager {
    private final List<Pet> pets = new ArrayList<>();
    private final FileManager fileManager;

    public PetManager(FileManager fileManager) {
        this.fileManager = fileManager;
    }

    public void load() { pets.clear(); pets.addAll(fileManager.loadPets()); }
    public void save() { fileManager.savePets(pets); }
    public void addPet(Pet pet) {
        if (findById(pet.getPetId()) != null) throw new IllegalArgumentException("Duplicate pet ID");
        pets.add(pet);
        save();
    }
    public void updatePet(Pet pet) {
        if (findById(pet.getPetId()) == null) throw new IllegalArgumentException("Pet not found");
        save();
    }
    public void deletePet(String petId) {
        if (!pets.removeIf(pet -> pet.getPetId().equals(petId))) {
            throw new IllegalArgumentException("Pet not found");
        }
        save();
    }
    public List<Pet> searchPet(String term) {
        String needle = term == null ? "" : term.trim().toLowerCase();
        if (needle.isEmpty()) return getAllPets();
        return pets.stream().filter(p -> (p.getPetId() + " " + p.getName() + " " + p.getBreed()
                + " " + p.getType()).toLowerCase().contains(needle)).collect(Collectors.toList());
    }
    public Pet findById(String petId) {
        return pets.stream().filter(p -> p.getPetId().equals(petId)).findFirst().orElse(null);
    }
    public List<Pet> getAllPets() { return new ArrayList<>(pets); }
}
