public class Adopter {
    private String adopterId;
    private String name;
    private int age;
    private String gender;
    private String phone;
    private String email;
    private String address;
    private String registrationDate;

    public Adopter(String adopterId, String name, int age, String gender, String phone,
                   String email, String address, String registrationDate) {
        this.adopterId = adopterId;
        this.name = name;
        this.age = age;
        this.gender = gender;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.registrationDate = registrationDate;
    }

    public String getAdopterId() { return adopterId; }
    public String getName() { return name; }
    public int getAge() { return age; }
    public String getGender() { return gender; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public String getRegistrationDate() { return registrationDate; }

    public void setName(String name) { this.name = name; }
    public void setAge(int age) { this.age = age; }
    public void setGender(String gender) { this.gender = gender; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setEmail(String email) { this.email = email; }
    public void setAddress(String address) { this.address = address; }
}
