public class Pet {
    private String petId;
    private String name;
    private String type;
    private String breed;
    private int age;
    private String gender;
    private String colour;
    private String healthStatus;
    private String vaccinationStatus;
    private String description;
    private String registrationDate;
    private String adoptionStatus;
    private String imagePath;

    public Pet(String petId, String name, String type, String breed, int age, String gender,
               String colour, String healthStatus, String vaccinationStatus, String description,
               String registrationDate, String adoptionStatus, String imagePath) {
        this.petId = petId;
        this.name = name;
        this.type = type;
        this.breed = breed;
        this.age = age;
        this.gender = gender;
        this.colour = colour;
        this.healthStatus = healthStatus;
        this.vaccinationStatus = vaccinationStatus;
        this.description = description;
        this.registrationDate = registrationDate;
        this.adoptionStatus = adoptionStatus;
        this.imagePath = imagePath;
    }

    public String getPetId() { return petId; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getBreed() { return breed; }
    public int getAge() { return age; }
    public String getGender() { return gender; }
    public String getColour() { return colour; }
    public String getHealthStatus() { return healthStatus; }
    public String getVaccinationStatus() { return vaccinationStatus; }
    public String getDescription() { return description; }
    public String getRegistrationDate() { return registrationDate; }
    public String getAdoptionStatus() { return adoptionStatus; }
    public String getImagePath() { return imagePath; }

    public void setName(String name) { this.name = name; }
    public void setType(String type) { this.type = type; }
    public void setBreed(String breed) { this.breed = breed; }
    public void setAge(int age) { this.age = age; }
    public void setGender(String gender) { this.gender = gender; }
    public void setColour(String colour) { this.colour = colour; }
    public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }
    public void setVaccinationStatus(String vaccinationStatus) { this.vaccinationStatus = vaccinationStatus; }
    public void setDescription(String description) { this.description = description; }
    public void setAdoptionStatus(String adoptionStatus) { this.adoptionStatus = adoptionStatus; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public String display() {
        return petId + " - " + name + " (" + breed + ")";
    }
}
