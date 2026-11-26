import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

public class FileManager {
    private final Path dataDirectory;
    private final Path petsFile;
    private final Path adoptersFile;
    private final Path requestsFile;
    private final Path recordsFile;

    public FileManager() {
        String configuredDirectory = System.getenv("DATA_DIR");
        dataDirectory = configuredDirectory == null || configuredDirectory.isBlank()
                ? Paths.get(System.getProperty("user.dir"), "data")
                : Paths.get(configuredDirectory).toAbsolutePath().normalize();
        petsFile = dataDirectory.resolve("pets.csv");
        adoptersFile = dataDirectory.resolve("adopters.csv");
        requestsFile = dataDirectory.resolve("adoption_requests.csv");
        recordsFile = dataDirectory.resolve("adoption_records.csv");
    }

    public void ensureDataFiles() {
        try {
            Files.createDirectories(dataDirectory);
            createIfMissing(petsFile);
            createIfMissing(adoptersFile);
            createIfMissing(requestsFile);
            createIfMissing(recordsFile);
        } catch (IOException ex) {
            throw new RuntimeException("Unable to create data files: " + ex.getMessage(), ex);
        }
    }

    private void createIfMissing(Path file) throws IOException {
        if (!Files.exists(file)) Files.createFile(file);
    }

    public boolean isEmpty(Path file) throws IOException {
        return !Files.exists(file) || Files.size(file) == 0;
    }

    public void savePets(List<Pet> pets) {
        List<String[]> rows = new ArrayList<>();
        for (Pet pet : pets) {
            rows.add(new String[]{pet.getPetId(), pet.getName(), pet.getType(), pet.getBreed(),
                    Integer.toString(pet.getAge()), pet.getGender(), pet.getColour(),
                    pet.getHealthStatus(), pet.getVaccinationStatus(), pet.getDescription(),
                    pet.getRegistrationDate(), pet.getAdoptionStatus(), pet.getImagePath()});
        }
        writeCsv(petsFile, rows);
    }

    public List<Pet> loadPets() {
        List<Pet> pets = new ArrayList<>();
        for (String[] row : readCsv(petsFile)) {
            if (row.length >= 13) {
                try {
                    pets.add(new Pet(row[0], row[1], row[2], row[3], Integer.parseInt(row[4]),
                            row[5], row[6], row[7], row[8], row[9], row[10], row[11], row[12]));
                } catch (RuntimeException ex) {
                    System.err.println("Skipping corrupted pet record: " + String.join(",", row));
                }
            }
        }
        return pets;
    }

    public void saveAdopters(List<Adopter> adopters) {
        List<String[]> rows = new ArrayList<>();
        for (Adopter adopter : adopters) {
            rows.add(new String[]{adopter.getAdopterId(), adopter.getName(),
                    Integer.toString(adopter.getAge()), adopter.getGender(), adopter.getPhone(),
                    adopter.getEmail(), adopter.getAddress(), adopter.getRegistrationDate()});
        }
        writeCsv(adoptersFile, rows);
    }

    public List<Adopter> loadAdopters() {
        List<Adopter> adopters = new ArrayList<>();
        for (String[] row : readCsv(adoptersFile)) {
            if (row.length >= 8) {
                try {
                    adopters.add(new Adopter(row[0], row[1], Integer.parseInt(row[2]), row[3],
                            row[4], row[5], row[6], row[7]));
                } catch (RuntimeException ex) {
                    System.err.println("Skipping corrupted adopter record: " + String.join(",", row));
                }
            }
        }
        return adopters;
    }

    public void saveRequests(List<AdoptionRequest> requests) {
        List<String[]> rows = new ArrayList<>();
        for (AdoptionRequest request : requests) {
            rows.add(new String[]{request.getRequestId(), request.getAdopterId(), request.getPetId(),
                    request.getReason(), request.getRequestDate(), request.getStatus()});
        }
        writeCsv(requestsFile, rows);
    }

    public List<AdoptionRequest> loadRequests() {
        List<AdoptionRequest> requests = new ArrayList<>();
        for (String[] row : readCsv(requestsFile)) {
            if (row.length >= 6) {
                requests.add(new AdoptionRequest(row[0], row[1], row[2], row[3], row[4], row[5]));
            }
        }
        return requests;
    }

    public void saveAdoptionRecords(List<AdoptionRecord> records) {
        List<String[]> rows = new ArrayList<>();
        for (AdoptionRecord record : records) {
            rows.add(new String[]{record.getAdoptionId(), record.getPetId(), record.getAdopterId(),
                    record.getAdoptionDate(), record.getStatus()});
        }
        writeCsv(recordsFile, rows);
    }

    public List<AdoptionRecord> loadAdoptionRecords() {
        List<AdoptionRecord> records = new ArrayList<>();
        for (String[] row : readCsv(recordsFile)) {
            if (row.length >= 5) {
                records.add(new AdoptionRecord(row[0], row[1], row[2], row[3], row[4]));
            }
        }
        return records;
    }

    public void exportReport(List<String[]> reportRows, String fileName) {
        writeCsv(dataDirectory.resolve(fileName), reportRows);
    }

    public String getDataDirectory() { return dataDirectory.toString(); }

    private void writeCsv(Path path, List<String[]> rows) {
        Path temporary = path.resolveSibling(path.getFileName().toString() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            for (String[] row : rows) {
                writer.write(toCsvRow(row));
                writer.newLine();
            }
        } catch (IOException ex) {
            throw new RuntimeException("Unable to save " + path.getFileName() + ": " + ex.getMessage(), ex);
        }
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveError) {
                throw new RuntimeException("Unable to finalize " + path.getFileName(), moveError);
            }
        }
    }

    private List<String[]> readCsv(Path path) {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) rows.add(parseCsvLine(line));
            }
        } catch (IOException ex) {
            throw new RuntimeException("Unable to read " + path.getFileName() + ": " + ex.getMessage(), ex);
        }
        return rows;
    }

    private String toCsvRow(String[] values) {
        List<String> escaped = new ArrayList<>();
        for (String value : values) {
            String text = value == null ? "" : value;
            if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
                text = "\"" + text.replace("\"", "\"\"") + "\"";
            }
            escaped.add(text);
        }
        return String.join(",", escaped);
    }

    private String[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values.toArray(new String[0]);
    }
}
