import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class WebApplication {
    private final int port;
    private final byte[] expectedUsername;
    private final byte[] expectedPassword;
    private final FileManager fileManager;
    private final PetManager petManager;
    private final AdopterManager adopterManager;
    private final AdoptionManager adoptionManager;

    public WebApplication(int port, String username, String password, FileManager fileManager,
                          PetManager petManager, AdopterManager adopterManager,
                          AdoptionManager adoptionManager) {
        this.port = port;
        this.expectedUsername = username.getBytes(StandardCharsets.UTF_8);
        this.expectedPassword = password.getBytes(StandardCharsets.UTF_8);
        this.fileManager = fileManager;
        this.petManager = petManager;
        this.adopterManager = adopterManager;
        this.adoptionManager = adoptionManager;
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        System.out.println("Pet Adoption Center web app listening on port " + port);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if ("/health".equals(path)) {
                send(exchange, 200, "text/plain; charset=utf-8", "ok");
                return;
            }
            if (!authenticate(exchange)) {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"Pet Adoption Manager\", charset=\"UTF-8\"");
                send(exchange, 401, "text/plain; charset=utf-8", "Authentication required");
                return;
            }
            if (path.startsWith("/assets/")) {
                serveAsset(exchange, path);
                return;
            }
            if ("POST".equals(exchange.getRequestMethod())) {
                if (!sameOrigin(exchange)) {
                    send(exchange, 403, "text/plain; charset=utf-8", "Cross-origin form submission rejected");
                    return;
                }
                handleAction(exchange);
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod()) && !"HEAD".equals(exchange.getRequestMethod())) {
                send(exchange, 405, "text/plain; charset=utf-8", "Method not allowed");
                return;
            }
            if ("/reports.csv".equals(path)) {
                send(exchange, 200, "text/csv; charset=utf-8", reportCsv());
                return;
            }

            Map<String, String> query = parseForm(exchange.getRequestURI().getRawQuery());
            String page;
            String title;
            switch (path) {
                case "/pets" -> { page = petsPage(query.getOrDefault("q", ""), query.get("edit")); title = "Pet Management"; }
                case "/adopters" -> { page = adoptersPage(query.getOrDefault("q", ""), query.get("edit")); title = "Adopter Management"; }
                case "/requests" -> { page = requestsPage(query); title = "Adoption Requests"; }
                case "/records" -> { page = recordsPage(query.get("adopter")); title = "Adoption Records"; }
                case "/reports" -> { page = reportsPage(); title = "Reports & Statistics"; }
                case "/" -> { page = dashboardPage(); title = "Dashboard"; }
                default -> {
                    send(exchange, 404, "text/html; charset=utf-8",
                            layout("Page not found", "<section class=\"panel\"><h1>Page not found</h1><p>The requested page does not exist.</p><a class=\"button\" href=\"/\">Back to dashboard</a></section>"));
                    return;
                }
            }
            send(exchange, 200, "text/html; charset=utf-8", layout(title, page));
        } catch (IllegalArgumentException ex) {
            sendIfOpen(exchange, 400, "text/html; charset=utf-8", layout("Invalid request",
                    "<section class=\"panel\"><h1>Could not complete that action</h1><p>" + e(ex.getMessage()) + "</p><a class=\"button\" href=\"/\">Back to dashboard</a></section>"));
        } catch (Exception ex) {
            System.err.println("Web request failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            sendIfOpen(exchange, 500, "text/html; charset=utf-8", layout("Application error",
                    "<section class=\"panel\"><h1>Something went wrong</h1><p>The request could not be completed. Please try again.</p><a class=\"button\" href=\"/\">Back to dashboard</a></section>"));
        } finally {
            exchange.close();
        }
    }

    private boolean authenticate(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Basic ")) return false;
        try {
            String decoded = new String(Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            if (separator < 0) return false;
            byte[] username = decoded.substring(0, separator).getBytes(StandardCharsets.UTF_8);
            byte[] password = decoded.substring(separator + 1).getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(expectedUsername, username)
                    && MessageDigest.isEqual(expectedPassword, password);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean sameOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (origin == null || host == null) return false;
        try {
            URI originUri = URI.create(origin);
            String forwardedProto = exchange.getRequestHeaders().getFirst("X-Forwarded-Proto");
            String expectedScheme = forwardedProto == null ? "http" : forwardedProto.split(",", 2)[0].trim();
            URI hostUri = URI.create(expectedScheme + "://" + host);
            return Objects.equals(originUri.getHost(), hostUri.getHost())
                    && effectivePort(originUri) == effectivePort(hostUri)
                    && Objects.equals(originUri.getScheme(), expectedScheme);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private void handleAction(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(1_000_001);
        if (body.length > 1_000_000) {
            send(exchange, 413, "text/plain; charset=utf-8", "Form is too large");
            return;
        }
        Map<String, String> form = parseForm(new String(body, StandardCharsets.UTF_8));
        String returnTo = safeReturnPath(form.get("returnTo"));
        String action = required(form, "action");
        switch (action) {
            case "addPet" -> addPet(form);
            case "updatePet" -> updatePet(form);
            case "deletePet" -> petManager.deletePet(required(form, "petId"));
            case "addAdopter" -> addAdopter(form);
            case "updateAdopter" -> updateAdopter(form);
            case "deleteAdopter" -> adopterManager.deleteAdopter(required(form, "adopterId"));
            case "submitRequest" -> submitRequest(form);
            case "approveRequest" -> adoptionManager.approveRequest(required(form, "requestId"));
            case "rejectRequest" -> adoptionManager.rejectRequest(required(form, "requestId"));
            default -> throw new IllegalArgumentException("Unknown action.");
        }
        exchange.getResponseHeaders().set("Location", returnTo);
        exchange.sendResponseHeaders(303, -1);
    }

    private void addPet(Map<String, String> form) {
        petManager.addPet(new Pet(nextId("PET", petManager.getAllPets().stream().map(Pet::getPetId).collect(Collectors.toList())),
                required(form, "name"), required(form, "type"), required(form, "breed"),
                age(form, 0, 30, "Pet age"), required(form, "gender"), optional(form, "colour"),
                optional(form, "healthStatus"), optional(form, "vaccinationStatus"),
                optional(form, "description"), LocalDate.now().toString(), "Available",
                optional(form, "imagePath")));
    }

    private void updatePet(Map<String, String> form) {
        Pet pet = petManager.findById(required(form, "petId"));
        if (pet == null) throw new IllegalArgumentException("Pet not found.");
        pet.setName(required(form, "name"));
        pet.setType(required(form, "type"));
        pet.setBreed(required(form, "breed"));
        pet.setAge(age(form, 0, 30, "Pet age"));
        pet.setGender(required(form, "gender"));
        pet.setColour(optional(form, "colour"));
        pet.setHealthStatus(optional(form, "healthStatus"));
        pet.setVaccinationStatus(optional(form, "vaccinationStatus"));
        pet.setDescription(optional(form, "description"));
        pet.setImagePath(optional(form, "imagePath"));
        petManager.updatePet(pet);
    }

    private void addAdopter(Map<String, String> form) {
        adopterManager.addAdopter(new Adopter(nextId("ADT", adopterManager.getAllAdopters().stream().map(Adopter::getAdopterId).collect(Collectors.toList())),
                required(form, "name"), age(form, 18, 100, "Adopter age"), required(form, "gender"),
                validPhone(required(form, "phone")), validEmail(required(form, "email")), required(form, "address"),
                LocalDate.now().toString()));
    }

    private void updateAdopter(Map<String, String> form) {
        Adopter adopter = adopterManager.findById(required(form, "adopterId"));
        if (adopter == null) throw new IllegalArgumentException("Adopter not found.");
        adopter.setName(required(form, "name"));
        adopter.setAge(age(form, 18, 100, "Adopter age"));
        adopter.setGender(required(form, "gender"));
        adopter.setPhone(validPhone(required(form, "phone")));
        adopter.setEmail(validEmail(required(form, "email")));
        adopter.setAddress(required(form, "address"));
        adopterManager.updateAdopter(adopter);
    }

    private void submitRequest(Map<String, String> form) {
        String adopterId = required(form, "adopterId");
        if (adopterManager.findById(adopterId) == null) throw new IllegalArgumentException("Adopter not found.");
        adoptionManager.submitRequest(new AdoptionRequest(
                nextId("REQ", adoptionManager.getAllRequests().stream().map(AdoptionRequest::getRequestId).collect(Collectors.toList())),
                adopterId, required(form, "petId"), required(form, "reason"), LocalDate.now().toString(), "Pending"));
    }

    private String dashboardPage() {
        long available = petManager.getAllPets().stream().filter(p -> "Available".equalsIgnoreCase(p.getAdoptionStatus())).count();
        long pending = adoptionManager.getAllRequests().stream().filter(r -> "Pending".equalsIgnoreCase(r.getStatus())).count();
        StringBuilder out = new StringBuilder("<h1>Welcome to your adoption center</h1><p class=\"lead\">A calm, clear overview of your pets and adoption activity.</p><div class=\"stats\">");
        out.append(stat("Registered pets", petManager.getAllPets().size()))
                .append(stat("Available pets", available))
                .append(stat("Adopters", adopterManager.getAllAdopters().size()))
                .append(stat("Pending requests", pending))
                .append("</div><div class=\"actions\">");
        out.append("<a class=\"action\" href=\"/pets\"><span>🐾</span><strong>Manage pets</strong><small>Add, edit, search, and update pet records</small></a>")
                .append("<a class=\"action\" href=\"/requests\"><span>♡</span><strong>Adoption requests</strong><small>Review and process applications</small></a>")
                .append("<a class=\"action\" href=\"/adopters\"><span>♧</span><strong>Manage adopters</strong><small>Keep adopter information organized</small></a>")
                .append("<a class=\"action\" href=\"/records\"><span>✓</span><strong>Adoption records</strong><small>View completed adoptions</small></a>")
                .append("</div><section class=\"panel\"><div class=\"panel-head\"><h2>Recent requests</h2><a href=\"/requests\">View all</a></div>")
                .append(requestTable(adoptionManager.getAllRequests().stream().limit(5).collect(Collectors.toList()), false))
                .append("</section>");
        return out.toString();
    }

    private String petsPage(String query, String editId) {
        List<Pet> pets = petManager.searchPet(query);
        StringBuilder out = new StringBuilder("<div class=\"page-head\"><div><h1>Pet management</h1><p class=\"lead\">Browse and maintain the center's pet records.</p></div><a class=\"button\" href=\"#add-pet\">＋ Add a pet</a></div>");
        out.append("<form class=\"search\" method=\"get\" action=\"/pets\"><input name=\"q\" placeholder=\"Search pets by name, type, or breed\" value=\"").append(e(query)).append("\"><button>Search</button></form>");
        out.append("<section class=\"panel\"><div class=\"table-wrap\"><table><thead><tr><th>Pet</th><th>Type / breed</th><th>Age</th><th>Status</th><th>Actions</th></tr></thead><tbody>");
        if (pets.isEmpty()) out.append("<tr><td colspan=\"5\" class=\"empty\">No pets found.</td></tr>");
        for (Pet pet : pets) {
            out.append("<tr><td><div class=\"pet-cell\">");
            if (pet.getImagePath() != null && !pet.getImagePath().isBlank()) {
                String image = Paths.get(pet.getImagePath()).getFileName().toString();
                out.append("<img class=\"pet-photo\" src=\"/assets/pet_images/").append(e(image)).append("\" alt=\"\">");
            }
            out.append("<span><strong>").append(e(pet.getName())).append("</strong><small>").append(e(pet.getPetId())).append("</small></span></div></td><td>")
                    .append(e(pet.getType())).append(" / ").append(e(pet.getBreed())).append("</td><td>")
                    .append(pet.getAge()).append("</td><td><span class=\"badge\">").append(e(pet.getAdoptionStatus())).append("</span></td><td class=\"row-actions\">")
                    .append("<a class=\"text-link\" href=\"/pets?edit=").append(url(pet.getPetId())).append("#add-pet\">Edit</a>")
                    .append(postButton("deletePet", "petId", pet.getPetId(), "/pets", "Delete", "danger"))
                    .append("</td></tr>");
        }
        out.append("</tbody></table></div></section>");
        Pet edit = editId == null ? null : petManager.findById(editId);
        out.append(petForm(edit));
        return out.toString();
    }

    private String adoptersPage(String query, String editId) {
        List<Adopter> adopters = adopterManager.searchAdopter(query);
        StringBuilder out = new StringBuilder("<div class=\"page-head\"><div><h1>Adopter management</h1><p class=\"lead\">Maintain contact information for prospective adopters.</p></div><a class=\"button\" href=\"#add-adopter\">＋ Add an adopter</a></div>");
        out.append("<form class=\"search\" method=\"get\" action=\"/adopters\"><input name=\"q\" placeholder=\"Search by name, ID, phone, or email\" value=\"").append(e(query)).append("\"><button>Search</button></form>");
        out.append("<section class=\"panel\"><div class=\"table-wrap\"><table><thead><tr><th>Adopter</th><th>Contact</th><th>Age</th><th>Registered</th><th>Action</th></tr></thead><tbody>");
        if (adopters.isEmpty()) out.append("<tr><td colspan=\"5\" class=\"empty\">No adopters found.</td></tr>");
        for (Adopter adopter : adopters) {
            out.append("<tr><td><strong>").append(e(adopter.getName())).append("</strong><small class=\"block\">").append(e(adopter.getAdopterId())).append("</small></td><td>")
                    .append(e(adopter.getPhone())).append("<small class=\"block\">").append(e(adopter.getEmail())).append("</small></td><td>")
                    .append(adopter.getAge()).append("</td><td>").append(e(adopter.getRegistrationDate())).append("</td><td class=\"row-actions\">")
                    .append("<a class=\"text-link\" href=\"/adopters?edit=").append(url(adopter.getAdopterId())).append("#add-adopter\">Edit</a> ")
                    .append("<a class=\"text-link\" href=\"/records?adopter=").append(url(adopter.getAdopterId())).append("\">History</a> ")
                    .append(postButton("deleteAdopter", "adopterId", adopter.getAdopterId(), "/adopters", "Delete", "danger"))
                    .append("</td></tr>");
        }
        Adopter edit = editId == null ? null : adopterManager.findById(editId);
        out.append("</tbody></table></div></section>").append(adopterForm(edit));
        return out.toString();
    }

    private String requestsPage(Map<String, String> query) {
        String term = query.getOrDefault("q", "").trim().toLowerCase(Locale.ROOT);
        String status = query.getOrDefault("status", "All");
        List<AdoptionRequest> filtered = adoptionManager.getAllRequests().stream().filter(request -> {
            if (!"All".equals(status) && !request.getStatus().equalsIgnoreCase(status)) return false;
            Pet pet = petManager.findById(request.getPetId());
            Adopter adopter = adopterManager.findById(request.getAdopterId());
            String searchable = request.getRequestId() + " "
                    + (pet == null ? "" : pet.getName()) + " "
                    + (adopter == null ? "" : adopter.getName());
            return searchable.toLowerCase(Locale.ROOT).contains(term);
        }).collect(Collectors.toList());
        return "<div class=\"page-head\"><div><h1>Adoption requests</h1><p class=\"lead\">Review applications and approve or reject pending requests.</p></div></div>"
                + requestForm() + "<section class=\"panel\"><div class=\"panel-head\"><h2>All requests</h2></div>"
                + "<form class=\"search\" method=\"get\" action=\"/requests\"><input name=\"q\" placeholder=\"Search requests, pets, and adopters\" value=\""
                + e(query.getOrDefault("q", "")) + "\"><select name=\"status\"><option" + selected("All", status)
                + ">All</option><option" + selected("Pending", status) + ">Pending</option><option"
                + selected("Approved", status) + ">Approved</option><option" + selected("Rejected", status)
                + ">Rejected</option></select><button>Filter</button></form>"
                + requestTable(filtered, true) + "</section>";
    }

    private String recordsPage(String adopterFilter) {
        StringBuilder out = new StringBuilder("<h1>Adoption records</h1><p class=\"lead\">Completed adoptions and their history.</p><section class=\"panel\"><div class=\"table-wrap\"><table><thead><tr><th>Record</th><th>Pet</th><th>Adopter</th><th>Date</th><th>Status</th></tr></thead><tbody>");
        List<AdoptionRecord> records = adoptionManager.getAdoptionRecords().stream()
                .filter(record -> adopterFilter == null || adopterFilter.equals(record.getAdopterId()))
                .collect(Collectors.toList());
        if (records.isEmpty()) out.append("<tr><td colspan=\"5\" class=\"empty\">No adoption records yet.</td></tr>");
        for (AdoptionRecord record : records) {
            Pet pet = petManager.findById(record.getPetId());
            Adopter adopter = adopterManager.findById(record.getAdopterId());
            out.append("<tr><td>").append(e(record.getAdoptionId())).append("</td><td>")
                    .append(e(pet == null ? record.getPetId() : pet.getName())).append("</td><td>")
                    .append(e(adopter == null ? record.getAdopterId() : adopter.getName())).append("</td><td>")
                    .append(e(record.getAdoptionDate())).append("</td><td><span class=\"badge success\">")
                    .append(e(record.getStatus())).append("</span></td></tr>");
        }
        return out.append("</tbody></table></div></section>").toString();
    }

    private String reportsPage() {
        long adopted = petManager.getAllPets().stream().filter(p -> "Adopted".equalsIgnoreCase(p.getAdoptionStatus())).count();
        long pending = adoptionManager.getAllRequests().stream().filter(r -> "Pending".equalsIgnoreCase(r.getStatus())).count();
        StringBuilder out = new StringBuilder("<div class=\"page-head\"><div><h1>Reports &amp; statistics</h1><p class=\"lead\">A quick summary of center activity.</p></div><a class=\"button\" href=\"/reports.csv\">Export CSV</a></div><div class=\"stats\">");
        out.append(stat("Total pets", petManager.getAllPets().size()))
                .append(stat("Adopted", adopted))
                .append(stat("Adopters", adopterManager.getAllAdopters().size()))
                .append(stat("Pending requests", pending))
                .append("</div>");
        return out.toString();
    }

    private String petForm(Pet pet) {
        boolean editing = pet != null;
        StringBuilder out = new StringBuilder("<section class=\"panel\" id=\"add-pet\"><h2>").append(editing ? "Edit pet" : "Add a new pet").append("</h2><form class=\"form-grid\" method=\"post\" action=\"/actions\">")
                .append(hidden("action", editing ? "updatePet" : "addPet"))
                .append(hidden("returnTo", "/pets"));
        if (editing) out.append(hidden("petId", pet.getPetId()));
        out.append(field("name", "Pet name", editing ? pet.getName() : "", "text", true))
                .append(field("type", "Type", editing ? pet.getType() : "", "text", true))
                .append(field("breed", "Breed", editing ? pet.getBreed() : "", "text", true))
                .append(field("age", "Age", editing ? String.valueOf(pet.getAge()) : "", "number", true))
                .append(field("gender", "Gender", editing ? pet.getGender() : "", "text", true))
                .append(field("colour", "Colour", editing ? pet.getColour() : "", "text", false))
                .append(field("healthStatus", "Health status", editing ? pet.getHealthStatus() : "Healthy", "text", false))
                .append(field("vaccinationStatus", "Vaccination status", editing ? pet.getVaccinationStatus() : "", "text", false))
                .append(field("imagePath", "Image path", editing ? pet.getImagePath() : "", "text", false))
                .append("<label class=\"wide\">Description<textarea name=\"description\" rows=\"3\">")
                .append(e(editing ? pet.getDescription() : "")).append("</textarea></label><div class=\"wide\"><button>")
                .append(editing ? "Save changes" : "Add pet").append("</button>");
        if (editing) out.append(" <a class=\"text-link\" href=\"/pets\">Cancel</a>");
        return out.append("</div></form></section>").toString();
    }

    private String adopterForm(Adopter adopter) {
        boolean editing = adopter != null;
        return "<section class=\"panel\" id=\"add-adopter\"><h2>" + (editing ? "Edit adopter" : "Register an adopter") + "</h2><form class=\"form-grid\" method=\"post\" action=\"/actions\">"
                + hidden("action", editing ? "updateAdopter" : "addAdopter") + hidden("returnTo", "/adopters")
                + (editing ? hidden("adopterId", adopter.getAdopterId()) : "")
                + field("name", "Full name", editing ? adopter.getName() : "", "text", true)
                + field("age", "Age", editing ? String.valueOf(adopter.getAge()) : "", "number", true)
                + field("gender", "Gender", editing ? adopter.getGender() : "", "text", true)
                + field("phone", "Phone", editing ? adopter.getPhone() : "", "tel", true)
                + field("email", "Email", editing ? adopter.getEmail() : "", "email", true)
                + "<label class=\"wide\">Address<textarea name=\"address\" rows=\"2\" required>"
                + e(editing ? adopter.getAddress() : "") + "</textarea></label>"
                + "<div class=\"wide\"><button>" + (editing ? "Save changes" : "Save adopter") + "</button>"
                + (editing ? " <a class=\"text-link\" href=\"/adopters\">Cancel</a>" : "")
                + "</div></form></section>";
    }

    private String requestForm() {
        StringBuilder out = new StringBuilder("<section class=\"panel\"><h2>Submit an adoption request</h2><form class=\"form-grid\" method=\"post\" action=\"/actions\">")
                .append(hidden("action", "submitRequest")).append(hidden("returnTo", "/requests"))
                .append("<label>Adopter<select name=\"adopterId\" required><option value=\"\">Choose adopter</option>");
        for (Adopter adopter : adopterManager.getAllAdopters()) {
            out.append("<option value=\"").append(e(adopter.getAdopterId())).append("\">")
                    .append(e(adopter.getName())).append(" (").append(e(adopter.getAdopterId())).append(")</option>");
        }
        out.append("</select></label><label>Available pet<select name=\"petId\" required><option value=\"\">Choose pet</option>");
        for (Pet pet : petManager.getAllPets()) {
            if ("Available".equalsIgnoreCase(pet.getAdoptionStatus())) {
                out.append("<option value=\"").append(e(pet.getPetId())).append("\">")
                        .append(e(pet.getName())).append(" — ").append(e(pet.getBreed())).append("</option>");
            }
        }
        return out.append("</select></label><label class=\"wide\">Reason<textarea name=\"reason\" rows=\"2\" required></textarea></label><div class=\"wide\"><button>Submit request</button></div></form></section>").toString();
    }

    private String requestTable(List<AdoptionRequest> requests, boolean actions) {
        StringBuilder out = new StringBuilder("<div class=\"table-wrap\"><table><thead><tr><th>Request</th><th>Pet</th><th>Adopter</th><th>Reason</th><th>Date</th><th>Status</th>");
        if (actions) out.append("<th>Action</th>");
        out.append("</tr></thead><tbody>");
        if (requests.isEmpty()) out.append("<tr><td colspan=\"").append(actions ? "7" : "6").append("\" class=\"empty\">No requests to display.</td></tr>");
        for (AdoptionRequest request : requests) {
            Pet pet = petManager.findById(request.getPetId());
            Adopter adopter = adopterManager.findById(request.getAdopterId());
            out.append("<tr><td>").append(e(request.getRequestId())).append("</td><td>")
                    .append(e(pet == null ? request.getPetId() : pet.getName())).append("</td><td>")
                    .append(e(adopter == null ? request.getAdopterId() : adopter.getName())).append("</td><td>")
                    .append(e(request.getReason())).append("</td><td>").append(e(request.getRequestDate()))
                    .append("</td><td><span class=\"badge\">").append(e(request.getStatus())).append("</span></td>");
            if (actions) {
                out.append("<td class=\"row-actions\">");
                if ("Pending".equalsIgnoreCase(request.getStatus())) {
                    out.append(postButton("approveRequest", "requestId", request.getRequestId(), "/requests", "Approve", "small"))
                            .append(postButton("rejectRequest", "requestId", request.getRequestId(), "/requests", "Reject", "danger small"));
                } else out.append("—");
                out.append("</td>");
            }
            out.append("</tr>");
        }
        return out.append("</tbody></table></div>").toString();
    }

    private String postButton(String action, String key, String value, String returnTo, String label, String style) {
        return "<form class=\"inline\" method=\"post\" action=\"/actions\">" + hidden("action", action)
                + hidden(key, value) + hidden("returnTo", returnTo)
                + "<button class=\"" + e(style) + "\" type=\"submit\">" + e(label) + "</button></form>";
    }

    private String stat(String label, long value) {
        return "<article class=\"stat\"><span>" + e(label) + "</span><strong>" + value + "</strong></article>";
    }

    private String field(String name, String label, String value, String type, boolean required) {
        return "<label>" + e(label) + "<input name=\"" + e(name) + "\" type=\"" + e(type)
                + "\" value=\"" + e(value) + "\"" + (required ? " required" : "") + "></label>";
    }

    private String hidden(String name, String value) {
        return "<input type=\"hidden\" name=\"" + e(name) + "\" value=\"" + e(value) + "\">";
    }

    private String layout(String title, String content) {
        String[] routes = {"/", "/pets", "/requests", "/adopters", "/records", "/reports"};
        String[] labels = {"Dashboard", "Pet Management", "Adoption Requests", "Adopter Management", "Adoption Records", "Reports & Statistics"};
        String path = "";
        StringBuilder nav = new StringBuilder();
        for (int i = 0; i < routes.length; i++) {
            if (title.equals(labels[i]) || ("/".equals(routes[i]) && "Dashboard".equals(title))) path = routes[i];
            nav.append("<a").append(routes[i].equals(path) ? " class=\"active\"" : "")
                    .append(" href=\"").append(routes[i]).append("\">").append(labels[i]).append("</a>");
        }
        StringBuilder page = new StringBuilder("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><meta name=\"theme-color\" content=\"#641e35\"><title>")
                .append(e(title)).append(" · Pet Adoption Center</title><style>")
                .append(styles()).append("</style></head><body><aside class=\"sidebar\"><a class=\"brand\" href=\"/\"><span class=\"paw\">🐾</span><span><b>PET ADOPTION CENTER</b><small>Connecting hearts, creating homes</small></span></a><nav>")
                .append(nav).append("</nav><div class=\"side-note\">Every pet deserves a loving home.</div></aside><main class=\"main\"><header><span>Pet Adoption Center Manager</span><span class=\"secure\">Private manager portal</span></header><div class=\"content\">")
                .append(content).append("</div><footer>Made with care for pets and their people.</footer></main></body></html>");
        return page.toString();
    }

    private String styles() {
        return """
                @font-face{font-family:Nunito;src:url('/assets/fonts/Nunito-Regular.ttf')}@font-face{font-family:Nunito;src:url('/assets/fonts/Nunito-700.ttf');font-weight:700}
                :root{--wine:#641e35;--deep:#451426;--rose:#7b3049;--pale:#f4e8ec;--cream:#fff9f3;--ink:#29212a;--muted:#655963;--line:#e6d5d9;--green:#287a50}
                *{box-sizing:border-box}body{margin:0;background:var(--cream);color:var(--ink);font:15px Nunito,'Segoe UI',sans-serif;display:flex;min-height:100vh}
                a{color:var(--wine);text-decoration:none}.sidebar{background:var(--deep);color:#fff;width:250px;min-height:100vh;padding:22px 14px;display:flex;flex-direction:column;flex-shrink:0}
                .brand{color:#fff;display:flex;gap:12px;align-items:center;padding:6px 8px 26px}.brand b{font-size:13px;letter-spacing:.04em}.brand small{display:block;color:#ebcbd5;font-size:12px;margin-top:3px}.paw{font-size:30px}
                nav{display:grid;gap:5px}nav a{color:#fff;padding:11px 13px;border-radius:12px;font-weight:700}nav a:hover,nav a.active{background:#7b3049;color:#fff}.side-note{margin-top:auto;color:#ebcbd5;font-size:13px;padding:12px}
                .main{flex:1;min-width:0}header{height:64px;border-bottom:1px solid var(--line);display:flex;align-items:center;justify-content:space-between;padding:0 34px;background:#fff;font-weight:700;color:var(--deep)}.secure{font-size:12px;color:var(--green);font-weight:700}
                .content{max-width:1360px;margin:auto;padding:30px 34px}h1{font-size:28px;line-height:1.2;color:var(--deep);margin:0 0 7px}h2{font-size:20px;color:var(--deep);margin:0 0 16px}.lead{color:var(--muted);margin:0 0 24px}.page-head,.panel-head{display:flex;align-items:center;justify-content:space-between;gap:14px}
                .stats{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:15px;margin:22px 0}.stat,.panel,.action{background:#fff;border:1px solid var(--line);border-radius:18px;box-shadow:0 5px 20px rgba(69,20,38,.045)}.stat{padding:19px}.stat span{color:var(--muted);font-weight:700;font-size:13px}.stat strong{display:block;color:var(--wine);font-size:28px;margin-top:8px}
                .actions{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px;margin:24px 0}.action{padding:18px;display:flex;flex-direction:column;gap:8px;transition:transform .16s ease,border-color .16s ease}.action:hover{transform:translateY(-2px);border-color:#c995a7}.action span{font-size:22px;color:var(--wine)}.action strong{color:var(--deep)}.action small{color:var(--muted);line-height:1.4}
                .panel{padding:20px;margin:20px 0}.panel-head{margin-bottom:14px}.panel-head h2{margin:0}.table-wrap{overflow-x:auto}table{width:100%;border-collapse:collapse;min-width:670px}th{text-align:left;color:var(--muted);font-size:12px;text-transform:uppercase;letter-spacing:.04em;background:#fff9f3}td,th{border-bottom:1px solid var(--line);padding:12px 10px;vertical-align:middle}td{font-size:14px}td small,.block{display:block;color:var(--muted);font-size:12px;margin-top:3px}.empty{text-align:center;color:var(--muted);padding:28px}.pet-cell{display:flex;align-items:center;gap:10px}.pet-photo{width:48px;height:48px;border-radius:12px;object-fit:cover;background:var(--pale)}
                .badge{display:inline-block;background:var(--pale);color:var(--wine);padding:5px 9px;border-radius:99px;font-weight:700;font-size:12px}.badge.success{background:#e5f3eb;color:var(--green)}button,.button{font:700 14px Nunito,sans-serif;background:var(--wine);color:#fff;border:0;border-radius:10px;padding:10px 14px;cursor:pointer;display:inline-block}button:hover,.button:hover{background:var(--deep);color:#fff}.danger{background:#fbe8e9;color:#98283a}.danger:hover{background:#98283a;color:#fff}.small{padding:7px 9px;font-size:12px}.text-link{font-weight:700}.inline{display:inline-block;margin:0 3px}.row-actions{white-space:nowrap}
                .search{display:flex;gap:8px;margin:18px 0}.search input{flex:1}.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.form-grid label{display:grid;gap:6px;font-weight:700;color:var(--deep);font-size:13px}.form-grid .wide{grid-column:1/-1}input,textarea,select{width:100%;border:1px solid var(--line);border-radius:10px;padding:10px 11px;background:#fff;color:var(--ink);font:15px Nunito,sans-serif}input:focus,textarea:focus,select:focus{outline:2px solid #e8c9d4;border-color:var(--rose)}textarea{resize:vertical}.form-grid button{margin-right:8px}
                footer{text-align:center;color:var(--muted);font-size:12px;padding:18px 34px 26px}
                @media(max-width:900px){.sidebar{width:205px}.stats,.actions{grid-template-columns:repeat(2,minmax(0,1fr))}.content{padding:24px}}
                @media(max-width:620px){body{display:block}.sidebar{width:100%;min-height:0;padding:12px}.brand{padding:5px 8px 12px}.side-note{display:none}nav{display:flex;overflow:auto}nav a{white-space:nowrap;font-size:13px;padding:9px}.main header{height:52px;padding:0 16px;font-size:13px}.content{padding:20px 14px}.stats,.actions{grid-template-columns:1fr 1fr;gap:9px}.stat{padding:14px}.stat strong{font-size:23px}.page-head{align-items:flex-start}.page-head .button{white-space:nowrap}.form-grid{grid-template-columns:1fr}.form-grid .wide{grid-column:auto}.secure{display:none}}
                @media(prefers-reduced-motion:reduce){*,*::before,*::after{scroll-behavior:auto!important;transition:none!important}}
                """;
    }

    private String reportCsv() {
        StringBuilder csv = new StringBuilder("Metric,Value\r\n");
        csv.append(csvRow("Total registered pets", String.valueOf(petManager.getAllPets().size())));
        csv.append(csvRow("Available pets", String.valueOf(petManager.getAllPets().stream().filter(p -> "Available".equalsIgnoreCase(p.getAdoptionStatus())).count())));
        csv.append(csvRow("Adopted pets", String.valueOf(petManager.getAllPets().stream().filter(p -> "Adopted".equalsIgnoreCase(p.getAdoptionStatus())).count())));
        csv.append(csvRow("Registered adopters", String.valueOf(adopterManager.getAllAdopters().size())));
        csv.append(csvRow("Pending requests", String.valueOf(adoptionManager.getAllRequests().stream().filter(r -> "Pending".equalsIgnoreCase(r.getStatus())).count())));
        csv.append(csvRow("Rejected requests", String.valueOf(adoptionManager.getAllRequests().stream().filter(r -> "Rejected".equalsIgnoreCase(r.getStatus())).count())));
        return csv.toString();
    }

    private String csvRow(String first, String second) {
        return "\"" + first.replace("\"", "\"\"") + "\",\"" + second.replace("\"", "\"\"") + "\"\r\n";
    }

    private void serveAsset(HttpExchange exchange, String path) throws IOException {
        Path assetRoot = Paths.get("assets").toAbsolutePath().normalize();
        Path asset = assetRoot.resolve(path.substring("/assets/".length())).normalize();
        if (!asset.startsWith(assetRoot) || !Files.isRegularFile(asset)) {
            send(exchange, 404, "text/plain; charset=utf-8", "Not found");
            return;
        }
        String file = asset.getFileName().toString().toLowerCase(Locale.ROOT);
        String type = file.endsWith(".jpg") || file.endsWith(".jpeg") ? "image/jpeg"
                : file.endsWith(".ttf") ? "font/ttf" : "application/octet-stream";
        byte[] bytes = Files.readAllBytes(asset);
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'; font-src 'self'; img-src 'self'; style-src 'unsafe-inline'");
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; form-action 'self'; base-uri 'none'; frame-ancestors 'none'");
        if ("HEAD".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(status, -1);
        } else {
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private void sendIfOpen(HttpExchange exchange, int status, String contentType, String body) {
        try {
            send(exchange, status, contentType, body);
        } catch (IOException ex) {
            System.err.println("Could not send web error response: " + ex.getMessage());
        }
    }

    private Map<String, String> parseForm(String raw) {
        Map<String, String> values = new HashMap<>();
        if (raw == null || raw.isEmpty()) return values;
        for (String pair : raw.split("&")) {
            int separator = pair.indexOf('=');
            String key = separator < 0 ? pair : pair.substring(0, separator);
            String value = separator < 0 ? "" : pair.substring(separator + 1);
            values.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return values;
    }

    private String required(Map<String, String> form, String name) {
        String value = form.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Please complete the required fields.");
        return value.trim();
    }

    private String optional(Map<String, String> form, String name) {
        return form.getOrDefault(name, "").trim();
    }

    private int number(Map<String, String> form, String name) {
        try {
            int value = Integer.parseInt(required(form, name));
            if (value < 0) throw new IllegalArgumentException("Age must be zero or greater.");
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Enter a valid age.");
        }
    }

    private int age(Map<String, String> form, int minimum, int maximum, String label) {
        int value = number(form, "age");
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(label + " must be between " + minimum + " and " + maximum + ".");
        }
        return value;
    }

    private String validPhone(String phone) {
        if (!phone.matches("[0-9()+ -]{7,15}")) throw new IllegalArgumentException("Enter a valid phone number.");
        return phone;
    }

    private String validEmail(String email) {
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        return email;
    }

    private String selected(String option, String value) {
        return option.equalsIgnoreCase(value) ? " selected" : "";
    }

    private String nextId(String prefix, List<String> ids) {
        int maximum = 0;
        for (String id : ids) {
            try {
                maximum = Math.max(maximum, Integer.parseInt(id.replaceAll("[^0-9]", "")));
            } catch (NumberFormatException ignored) {
                // Ignore records whose identifier does not use a numeric suffix.
            }
        }
        return prefix + String.format("%03d", maximum + 1);
    }

    private String safeReturnPath(String path) {
        if (path == null || !Set.of("/", "/pets", "/adopters", "/requests", "/records", "/reports").contains(path)) return "/";
        return path;
    }

    private String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String e(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
