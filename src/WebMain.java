import java.io.IOException;
import java.nio.file.*;

public class WebMain {
    public static void main(String[] args) throws IOException {
        String username = System.getenv("APP_USERNAME");
        String password = System.getenv("APP_PASSWORD");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("Set APP_USERNAME and APP_PASSWORD before starting the web application.");
        }

        FileManager fileManager = new FileManager();
        fileManager.ensureDataFiles();
        initializePersistentData(fileManager);

        PetManager petManager = new PetManager(fileManager);
        AdopterManager adopterManager = new AdopterManager(fileManager);
        petManager.load();
        adopterManager.load();
        AdoptionManager adoptionManager = new AdoptionManager(fileManager, petManager);
        adoptionManager.load();

        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        new WebApplication(port, username, password, fileManager, petManager,
                adopterManager, adoptionManager).start();
    }

    private static void initializePersistentData(FileManager fileManager) throws IOException {
        Path seedDirectory = Paths.get("seed-data");
        Path dataDirectory = Paths.get(fileManager.getDataDirectory());
        String[] files = {"pets.csv", "adopters.csv", "adoption_requests.csv", "adoption_records.csv"};
        for (String file : files) {
            Path target = dataDirectory.resolve(file);
            Path seed = seedDirectory.resolve(file);
            if (Files.size(target) == 0 && Files.isRegularFile(seed)) {
                Files.copy(seed, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
