import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

public class Dashboard extends JFrame {
    static final Color BURGUNDY = new Color(100, 30, 53);
    static final Color DEEP_BURGUNDY = new Color(69, 20, 38);
    static final Color DARK_WINE = new Color(53, 14, 29);
    static final Color SECONDARY_BURGUNDY = new Color(123, 48, 73);
    static final Color LIGHT_BURGUNDY = new Color(244, 232, 236);
    static final Color CREAM = new Color(255, 249, 243);
    static final Color BEIGE = LIGHT_BURGUNDY;
    static final Color ROSE_GOLD = new Color(235, 203, 213);
    static final Color LIGHT_PINK = ROSE_GOLD;
    static final Color DARK_TEXT = new Color(41, 33, 42);
    static final Color SECONDARY_TEXT = new Color(101, 89, 99);
    static final Color SUCCESS = new Color(40, 122, 80);
    static final Color WARNING = new Color(184, 106, 32);
    static final Color DANGER = new Color(184, 50, 66);
    static final Color BORDER = new Color(230, 213, 217);

    private final FileManager fileManager = new FileManager();
    private final PetManager petManager = new PetManager(fileManager);
    private final AdopterManager adopterManager = new AdopterManager(fileManager);
    private final AdoptionManager adoptionManager = new AdoptionManager(fileManager, petManager);

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JLabel dateLabel = new JLabel();
    private final JLabel pageTitle = new JLabel();
    private JButton selectedNav;
    private PawCursorOverlay pawCursor;
    private Cursor blankCursor;
    private final Set<Component> cursorHooked = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<Integer, Font> nunitoFonts = new HashMap<>();
    private final Map<String, ImageIcon> imageCache = new HashMap<>();
    private final Map<String, BufferedImage> sourceImageCache = new HashMap<>();
    private final Set<String> missingImagePaths = new HashSet<>();
    private final Map<String, JButton> navButtons = new LinkedHashMap<>();
    private final List<PawTrail> pawTrail = new ArrayList<>();
    private final List<PawRipple> pawRipples = new ArrayList<>();
    private boolean cursorOverButton;
    private int cursorMotionStep;
    private Point lastCursorPoint;
    private javax.swing.Timer cursorAnimationTimer;
    private javax.swing.Timer petSearchDebounce;
    private String currentPage;

    public Dashboard() {
        super("Pet Adoption Center Manager");
        loadNunitoFonts();
        UIManager.put("Label.foreground", DARK_TEXT);
        UIManager.put("TextField.foreground", DARK_TEXT);
        UIManager.put("TextField.caretForeground", BURGUNDY);
        UIManager.put("TextArea.foreground", DARK_TEXT);
        UIManager.put("ComboBox.foreground", DARK_TEXT);
        UIManager.put("Table.foreground", DARK_TEXT);
        UIManager.put("Table.background", Color.WHITE);
        UIManager.put("Label.font", uiFont(400, 15));
        UIManager.put("TextField.font", uiFont(400, 15));
        UIManager.put("TextArea.font", uiFont(400, 15));
        UIManager.put("ComboBox.font", uiFont(500, 15));
        UIManager.put("Button.font", uiFont(600, 14));
        UIManager.put("Table.font", uiFont(400, 14));
        UIManager.put("TableHeader.font", uiFont(700, 14));
        UIManager.put("OptionPane.messageFont", uiFont(400, 15));
        UIManager.put("OptionPane.buttonFont", uiFont(600, 14));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        GraphicsConfiguration configuration = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
        double scaleX = configuration.getDefaultTransform().getScaleX();
        double scaleY = configuration.getDefaultTransform().getScaleY();
        int availableWidth = (int) (screen.width / scaleX) - 48;
        int availableHeight = (int) (screen.height / scaleY) - 90;
        int windowWidth = Math.min(1400, Math.max(640, availableWidth));
        int windowHeight = Math.min(850, Math.max(520, availableHeight));
        setSize(windowWidth, windowHeight);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(Math.min(900, windowWidth), Math.min(600, windowHeight)));
        setLayout(new BorderLayout());
        initializeData();
        add(createSidebar(), BorderLayout.WEST);
        add(createMainArea(), BorderLayout.CENTER);
        installPawCursor();
        javax.swing.Timer clock = new javax.swing.Timer(1000, e -> dateLabel.setText(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy  |  hh:mm:ss a"))));
        clock.setCoalesce(true);
        clock.start();
        showPage("Dashboard");
    }

    private void loadNunitoFonts() {
        int[] weights = {400, 500, 600, 700, 800};
        for (int weight : weights) {
            File fontFile = Paths.get("assets", "fonts", "Nunito-" + (weight == 400 ? "Regular" : weight) + ".ttf").toFile();
            if (!fontFile.isFile()) continue;
            try {
                Font font = Font.createFont(Font.TRUETYPE_FONT, fontFile);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
                nunitoFonts.put(weight, font);
            } catch (FontFormatException | IOException ex) {
                System.err.println("Could not load Nunito " + weight + " font: " + ex.getMessage());
            }
        }
    }

    private Font uiFont(int weight, int size) {
        Font face = nunitoFonts.get(weight);
        if (face == null) {
            face = new Font("Nunito", weight >= 600 ? Font.BOLD : Font.PLAIN, size);
        }
        return face.deriveFont((float) size);
    }

    private void initializeData() {
        fileManager.ensureDataFiles();
        boolean petsEmpty = false, adoptersEmpty = false, requestsEmpty = false, recordsEmpty = false;
        try {
            petsEmpty = fileManager.isEmpty(Paths.get(fileManager.getDataDirectory(), "pets.csv"));
            adoptersEmpty = fileManager.isEmpty(Paths.get(fileManager.getDataDirectory(), "adopters.csv"));
            requestsEmpty = fileManager.isEmpty(Paths.get(fileManager.getDataDirectory(), "adoption_requests.csv"));
            recordsEmpty = fileManager.isEmpty(Paths.get(fileManager.getDataDirectory(), "adoption_records.csv"));
        } catch (IOException ex) {
            showError("Could not inspect data files: " + ex.getMessage());
        }
        petManager.load();
        adopterManager.load();
        adoptionManager.load();

        if (petsEmpty) {
            String today = LocalDate.now().toString();
            petManager.addPet(new Pet("PET001","Bruno","Dog","Golden Retriever",3,"Male","Golden","Healthy","Fully Vaccinated","Friendly, energetic and great with children.",today,"Adopted","assets/pet_images/bruno.jpg"));
            petManager.addPet(new Pet("PET002","Bella","Cat","Persian",2,"Female","Cream","Healthy","Fully Vaccinated","Calm indoor cat who loves soft blankets.",today,"Available","assets/pet_images/bella.jpg"));
            petManager.addPet(new Pet("PET003","Max","Dog","Beagle",4,"Male","Tri-colour","Healthy","Fully Vaccinated","Playful explorer with excellent manners.",today,"Available","assets/pet_images/max.jpg"));
            petManager.addPet(new Pet("PET004","Coco","Rabbit","Mini Lop",1,"Female","White","Healthy","Vaccinated","Gentle rabbit and an easy companion.",today,"Available","assets/pet_images/coco.jpg"));
            petManager.addPet(new Pet("PET005","Milo","Bird","Cockatiel",2,"Male","Grey","Healthy","Not Required","Whistles happily and enjoys attention.",today,"Available","assets/pet_images/milo.jpg"));
            petManager.addPet(new Pet("PET006","Luna","Cat","Siamese",3,"Female","Cream","Healthy","Fully Vaccinated","Quiet, affectionate and litter trained.",today,"Available","assets/pet_images/luna.jpg"));
            petManager.addPet(new Pet("PET007","Charlie","Rabbit","Dutch",1,"Male","Black and White","Under Observation","Vaccinated","Curious rabbit with a cheerful personality.",today,"Available","assets/pet_images/luna.jpg"));
            petManager.addPet(new Pet("PET008","Daisy","Dog","Labrador",5,"Female","Yellow","Healthy","Fully Vaccinated","Gentle, loyal and excellent with families.",today,"Adopted","assets/pet_images/daisy.jpg"));
        }
        if (adoptersEmpty) {
            String today = LocalDate.now().toString();
            adopterManager.addAdopter(new Adopter("ADT001","Ananya Rao",28,"Female","9876543210","ananya@example.com","12 Lake View Road, Bengaluru",today));
            adopterManager.addAdopter(new Adopter("ADT002","Rahul Mehta",35,"Male","9898989898","rahul@example.com","45 Residency Road, Bengaluru",today));
            adopterManager.addAdopter(new Adopter("ADT003","Sara Khan",26,"Female","9765432108","sara@example.com","8 Green Park, Bengaluru",today));
            adopterManager.addAdopter(new Adopter("ADT004","Vikram Singh",31,"Male","9123456780","vikram@example.com","22 Hill Street, Bengaluru",today));
            adopterManager.addAdopter(new Adopter("ADT005","Meera Iyer",29,"Female","9988776655","meera@example.com","5 Ashok Nagar, Bengaluru",today));
        }
        if (requestsEmpty) {
            String today = LocalDate.now().toString();
            adoptionManager.submitRequest(new AdoptionRequest("REQ001","ADT001","PET002","I would like to provide Bella a calm, loving home.","2026-11-20","Pending"));
            adoptionManager.submitRequest(new AdoptionRequest("REQ002","ADT003","PET004","My family has experience caring for small rabbits.","2026-11-22","Pending"));
            adoptionManager.submitRequest(new AdoptionRequest("REQ003","ADT005","PET006","Luna would be ideal for my quiet apartment.","2026-11-24","Pending"));
        }
        if (recordsEmpty) {
            fileManager.saveAdoptionRecords(List.of(
                    new AdoptionRecord("AD001","PET001","ADT002","2026-11-18","Completed"),
                    new AdoptionRecord("AD002","PET008","ADT004","2026-11-23","Completed")));
            adoptionManager.load();
        }
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setPreferredSize(new Dimension(245, 850));
        sidebar.setBackground(DEEP_BURGUNDY);
        sidebar.setLayout(new BorderLayout());

        JPanel brand = new JPanel(new BorderLayout());
        brand.setOpaque(false);
        brand.setBorder(new EmptyBorder(20, 18, 18, 12));
        JLabel paw = new JLabel(new PawIcon(34, ROSE_GOLD));
        paw.setHorizontalAlignment(SwingConstants.CENTER);
        paw.setPreferredSize(new Dimension(52, 52));
        JPanel brandText = new JPanel(new GridLayout(2, 1));
        brandText.setOpaque(false);
        JLabel title = new JLabel("PET ADOPTION CENTER");
        title.setForeground(Color.WHITE);
        title.setFont(uiFont(800, 14));
        JLabel subtitle = new JLabel("Connecting Hearts, Creating Homes.");
        subtitle.setForeground(ROSE_GOLD);
        subtitle.setFont(uiFont(500, 12));
        brandText.add(title);
        brandText.add(subtitle);
        brand.add(paw, BorderLayout.WEST);
        brand.add(brandText, BorderLayout.CENTER);
        sidebar.add(brand, BorderLayout.NORTH);

        JPanel nav = new JPanel();
        nav.setOpaque(false);
        nav.setLayout(new GridLayout(9, 1, 0, 3));
        nav.setBorder(new EmptyBorder(8, 10, 8, 10));
        String[] items = {"Dashboard","Pet Management","Add New Pet","Adoption Requests","Adopter Management","Adoption Records","Reports & Statistics","About Project","Exit Application"};
        String[] icons = {"home","paw","add","heart","people","records","chart","info","exit"};
        for (int i = 0; i < items.length; i++) {
            final String name = items[i];
            JButton button = makeNavButton(name, sidebarIcon(icons[i], CREAM));
            navButtons.put(name, button);
            button.addActionListener(e -> {
                if (name.equals("Exit Application")) {
                    int answer = JOptionPane.showConfirmDialog(this, "Exit Pet Adoption Center Manager?",
                            "Confirm Exit", JOptionPane.YES_NO_OPTION);
                    if (answer == JOptionPane.YES_OPTION) System.exit(0);
                } else showPage(name);
            });
            nav.add(button);
        }
        sidebar.add(nav, BorderLayout.CENTER);
        return sidebar;
    }

    private JButton makeNavButton(String text, Icon icon) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fill = getBackground();
                if (getModel().isPressed()) fill = DARK_WINE;
                else if (getModel().isRollover() && this != selectedNav) fill = SECONDARY_BURGUNDY;
                setForeground(getModel().isPressed() || this != selectedNav ? CREAM : BURGUNDY);
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
                super.paintComponent(graphics);
            }
        };
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setIcon(icon);
        button.setIconTextGap(14);
        button.setForeground(CREAM);
        button.setBackground(DEEP_BURGUNDY);
        button.setBorder(new EmptyBorder(12, 14, 12, 10));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setFont(uiFont(600, 15));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button != selectedNav) button.setBackground(SECONDARY_BURGUNDY);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (button != selectedNav) button.setBackground(DEEP_BURGUNDY);
            }
        });
        return button;
    }

    private JPanel createMainArea() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(CREAM);
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(Color.WHITE);
        top.setBorder(new CompoundBorder(new MatteBorder(0,0,1,0,BORDER), new EmptyBorder(14,28,14,28)));
        pageTitle.setFont(uiFont(700, 28));
        pageTitle.setForeground(DEEP_BURGUNDY);
        top.add(pageTitle, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
        right.setOpaque(false);
        dateLabel.setFont(uiFont(400, 13));
        dateLabel.setForeground(SECONDARY_TEXT);
        JLabel bell = new JLabel(new QuickActionIcon("info", SECONDARY_BURGUNDY));
        JLabel profile = new JLabel("  Administrator  ⌄ ");
        profile.setOpaque(true);
        profile.setBackground(BEIGE);
        profile.setBorder(new CompoundBorder(new LineBorder(ROSE_GOLD,1,true), new EmptyBorder(7,10,7,10)));
        right.add(bell); right.add(dateLabel); right.add(profile);
        top.add(right, BorderLayout.EAST);
        main.add(top, BorderLayout.NORTH);

        content.setBorder(new EmptyBorder(18,28,20,28));
        content.setBackground(CREAM);
        content.add(createDashboardPage(), "Dashboard");
        content.add(createPetPage(), "Pet Management");
        content.add(createAddPetPage(), "Add New Pet");
        content.add(createRequestsPage(), "Adoption Requests");
        content.add(createAdoptersPage(), "Adopter Management");
        content.add(createRecordsPage(), "Adoption Records");
        content.add(createReportsPage(), "Reports & Statistics");
        content.add(createAboutPage(), "About Project");
        applyReadableText(content);
        main.add(content, BorderLayout.CENTER);
        return main;
    }

    private void applyReadableText(Component component) {
        if (component instanceof JTableHeader) {
            component.setForeground(DARK_TEXT);
            component.setFont(uiFont(700, 14));
        } else if (component instanceof JLabel) {
            Font old = component.getFont();
            int size = old.getSize();
            if (size <= 12) size = 14;
            else if (size <= 14) size = 15;
            int weight = old.isBold() ? 700 : 500;
            component.setFont(uiFont(weight, size));
        } else if (component instanceof JTextField || component instanceof JTextArea
                || component instanceof JComboBox<?> || component instanceof JTable) {
            component.setForeground(DARK_TEXT);
            component.setFont(uiFont(400, 15));
        } else if (component instanceof JButton) {
            JButton button = (JButton) component;
            Color foreground = button.getForeground();
            if (Color.WHITE.equals(foreground)) {
                button.setForeground(Color.WHITE);
            } else if ("Delete".equalsIgnoreCase(button.getText())) {
                button.setForeground(DANGER);
            } else {
                button.setForeground(BURGUNDY);
            }
            button.setFont(uiFont(600, 14));
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                applyReadableText(child);
            }
        }
    }

    private void showPage(String name) {
        if (name.equals(currentPage)) return;
        currentPage = name;
        cards.show(content, name);
        pageTitle.setText(name);
        highlightNav(name);
        if (name.equals("Pet Management")) refreshPetPages();
        if (name.equals("Adopter Management")) refreshAdopterPage();
        if (name.equals("Adoption Requests")) refreshRequestPage();
        if (name.equals("Adoption Records")) refreshRecordPage();
        if (name.equals("Reports & Statistics")) refreshReportsPage();
    }

    private void highlightNav(String name) {
        if (selectedNav != null) {
            selectedNav.setBackground(DEEP_BURGUNDY);
            selectedNav.setForeground(CREAM);
        }
        selectedNav = navButtons.get(name);
        if (selectedNav != null) {
            selectedNav.setBackground(LIGHT_BURGUNDY);
            selectedNav.setForeground(BURGUNDY);
        }
    }

    private void installPawCursor() {
        JLayeredPane layeredPane = getLayeredPane();
        pawCursor = new PawCursorOverlay();
        layeredPane.add(pawCursor, JLayeredPane.DRAG_LAYER);
        pawCursor.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
        layeredPane.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                pawCursor.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
            }
        });

        BufferedImage blankImage = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(blankImage, new Point(0, 0), "hidden-pointer");
        setCursor(blankCursor);
        installPawCursorListeners(layeredPane, layeredPane);
        cursorAnimationTimer = new javax.swing.Timer(35, e -> advanceCursorEffects());
        cursorAnimationTimer.setCoalesce(true);
    }

    private void installPawCursorListeners(Component component, JLayeredPane layeredPane) {
        if (component == pawCursor) return;
        boolean newlyHooked = cursorHooked.add(component);
        if (newlyHooked) {
            component.setCursor(blankCursor);
            component.addMouseMotionListener(new MouseMotionAdapter() {
            private void update(MouseEvent event) {
                Point point = SwingUtilities.convertPoint(component, event.getPoint(), layeredPane);
                pawCursor.setPointer(point);
                if (lastCursorPoint != null && lastCursorPoint.distance(point) >= 24) {
                    if (pawTrail.size() == 7) pawTrail.remove(0);
                    pawTrail.add(new PawTrail(lastCursorPoint.x, lastCursorPoint.y, 0.46f));
                    ensureCursorAnimation();
                }
                lastCursorPoint = point;
            }
            @Override public void mouseMoved(MouseEvent event) { update(event); }
            @Override public void mouseDragged(MouseEvent event) { update(event); }
            });
            component.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent event) {
                if (component instanceof AbstractButton) {
                    cursorOverButton = true;
                    ensureCursorAnimation();
                }
            }
            @Override public void mouseExited(MouseEvent event) {
                if (component instanceof AbstractButton) {
                    cursorOverButton = false;
                    stopCursorAnimationWhenIdle();
                }
            }
            @Override public void mouseClicked(MouseEvent event) {
                Point point = SwingUtilities.convertPoint(component, event.getPoint(), layeredPane);
                if (pawRipples.size() == 2) pawRipples.remove(0);
                pawRipples.add(new PawRipple(point.x, point.y));
                ensureCursorAnimation();
            }
            });
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                installPawCursorListeners(child, layeredPane);
            }
        }
    }

    private void ensureCursorAnimation() {
        if (cursorAnimationTimer != null && !cursorAnimationTimer.isRunning()) cursorAnimationTimer.start();
    }

    private void stopCursorAnimationWhenIdle() {
        if (!cursorOverButton && pawTrail.isEmpty() && pawRipples.isEmpty()
                && cursorAnimationTimer != null) cursorAnimationTimer.stop();
    }

    private void advanceCursorEffects() {
        cursorMotionStep++;
        pawTrail.removeIf(mark -> {
            mark.alpha -= 0.035f;
            return mark.alpha <= 0;
        });
        pawRipples.removeIf(ripple -> {
            ripple.radius += 1.15;
            ripple.alpha -= 0.045f;
            return ripple.alpha <= 0;
        });
        pawCursor.repaint();
        stopCursorAnimationWhenIdle();
    }

    private static void drawPaw(Graphics2D graphics, double x, double y, double size, Color color, float alpha) {
        Graphics2D paw = (Graphics2D) graphics.create();
        paw.translate(x, y);
        paw.scale(size / 32.0, size / 32.0);
        paw.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, Math.min(1, alpha))));
        paw.setColor(color);
        paw.fill(new Ellipse2D.Double(3.2, 7.0, 6.3, 7.1));
        paw.fill(new Ellipse2D.Double(10.2, 2.0, 6.6, 8.0));
        paw.fill(new Ellipse2D.Double(18.0, 2.0, 6.6, 8.0));
        paw.fill(new Ellipse2D.Double(24.8, 7.0, 6.0, 7.1));
        Path2D pad = new Path2D.Double();
        pad.moveTo(16.0, 29.0);
        pad.curveTo(13.6, 28.5, 6.0, 25.2, 5.8, 20.5);
        pad.curveTo(5.5, 16.7, 9.0, 14.2, 12.0, 16.1);
        pad.curveTo(14.1, 12.5, 18.0, 12.5, 20.1, 16.1);
        pad.curveTo(23.8, 14.0, 27.1, 16.8, 26.5, 20.5);
        pad.curveTo(26.0, 24.7, 19.4, 28.6, 16.0, 29.0);
        pad.closePath();
        paw.fill(pad);
        paw.dispose();
    }

    private class PawCursorOverlay extends JComponent {
        private Point pointer = new Point(-100, -100);

        PawCursorOverlay() {
            setOpaque(false);
            setFocusable(false);
        }

        void setPointer(Point point) {
            pointer = point;
            repaint();
        }

        @Override public boolean contains(int x, int y) { return false; }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (PawTrail mark : pawTrail) drawPaw(g2, mark.x - 8, mark.y - 8, 16, ROSE_GOLD, mark.alpha);
            for (PawRipple ripple : pawRipples) {
                g2.setColor(new Color(BURGUNDY.getRed(), BURGUNDY.getGreen(), BURGUNDY.getBlue(),
                        Math.max(0, Math.min(255, (int) (ripple.alpha * 255)))));
                g2.setStroke(new BasicStroke(1.4f));
                double diameter = ripple.radius * 2;
                g2.draw(new Ellipse2D.Double(ripple.x - ripple.radius, ripple.y - ripple.radius,
                        diameter, diameter));
                drawPaw(g2, ripple.x - 6, ripple.y - 6, 12, BURGUNDY, ripple.alpha);
            }
            if (pointer.x >= 0 && pointer.y >= 0) {
                double bounce = cursorOverButton ? Math.abs(Math.sin(cursorMotionStep * 0.45)) * 2.2 : 0;
                drawPaw(g2, pointer.x - 10, pointer.y - 10 - bounce, 20, BURGUNDY, 1f);
            }
            g2.dispose();
        }
    }

    private static class PawTrail {
        final int x, y;
        float alpha;
        PawTrail(int x, int y, float alpha) { this.x = x; this.y = y; this.alpha = alpha; }
    }

    private static class PawRipple {
        final int x, y;
        double radius = 3;
        float alpha = 0.8f;
        PawRipple(int x, int y) { this.x = x; this.y = y; }
    }

    private Icon sidebarIcon(String key, Color color) {
        return "paw".equals(key) ? new PawIcon(20, color) : new QuickActionIcon(key, color);
    }

    private Icon statIcon(String key, Color color) {
        return "paw".equals(key) ? new PawIcon(24, color) : new QuickActionIcon(key, color);
    }

    private static class QuickActionIcon implements Icon {
        private final String kind;
        private final Color color;

        QuickActionIcon(String kind, Color color) {
            this.kind = kind;
            this.color = color;
        }

        @Override public int getIconWidth() { return 22; }
        @Override public int getIconHeight() { return 22; }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.translate(x, y);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (kind) {
                case "add":
                    g2.drawLine(11, 4, 11, 18);
                    g2.drawLine(4, 11, 18, 11);
                    break;
                case "heart":
                    Path2D heart = new Path2D.Double();
                    heart.moveTo(11, 19);
                    heart.curveTo(8, 16, 2, 12, 3, 7);
                    heart.curveTo(4, 2, 9, 3, 11, 7);
                    heart.curveTo(14, 2, 19, 3, 19, 8);
                    heart.curveTo(19, 12, 14, 16, 11, 19);
                    heart.closePath();
                    g2.fill(heart);
                    break;
                case "people":
                case "person-add":
                    g2.draw(new Ellipse2D.Double(4, 3, 6, 6));
                    g2.draw(new Ellipse2D.Double(13, 5, 5, 5));
                    g2.drawArc(1, 10, 13, 10, 0, 180);
                    g2.drawArc(11, 12, 10, 8, 0, 180);
                    if ("person-add".equals(kind)) {
                        g2.drawLine(17, 1, 17, 7);
                        g2.drawLine(14, 4, 20, 4);
                    }
                    break;
                case "records":
                    g2.drawRoundRect(4, 2, 14, 18, 2, 2);
                    g2.drawLine(7, 7, 15, 7);
                    g2.drawLine(7, 11, 15, 11);
                    g2.drawLine(7, 15, 13, 15);
                    break;
                case "home":
                    g2.drawLine(2, 10, 11, 3);
                    g2.drawLine(11, 3, 20, 10);
                    g2.drawRect(5, 9, 12, 10);
                    g2.drawRect(9, 13, 4, 6);
                    break;
                case "clock":
                    g2.draw(new Ellipse2D.Double(3, 3, 16, 16));
                    g2.drawLine(11, 6, 11, 11);
                    g2.drawLine(11, 11, 15, 13);
                    break;
                case "cross":
                    g2.drawLine(5, 5, 17, 17);
                    g2.drawLine(17, 5, 5, 17);
                    break;
                case "chart":
                    g2.drawLine(3, 19, 19, 19);
                    g2.drawLine(4, 17, 4, 12);
                    g2.drawLine(9, 17, 9, 7);
                    g2.drawLine(14, 17, 14, 10);
                    g2.drawLine(19, 17, 19, 3);
                    break;
                case "info":
                    g2.draw(new Ellipse2D.Double(3, 3, 16, 16));
                    g2.drawLine(11, 10, 11, 16);
                    g2.fill(new Ellipse2D.Double(10, 6, 2, 2));
                    break;
                case "exit":
                    g2.drawRoundRect(3, 3, 10, 16, 2, 2);
                    g2.drawLine(10, 11, 20, 11);
                    g2.drawLine(16, 7, 20, 11);
                    g2.drawLine(16, 15, 20, 11);
                    break;
                default:
                    g2.draw(new Ellipse2D.Double(4, 4, 14, 14));
            }
            g2.dispose();
        }
    }

    private static class PawIcon implements Icon {
        private final int size;
        private final Color color;
        PawIcon(int size, Color color) { this.size = size; this.color = color; }
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            drawPaw(g2, x, y, size, color, 1f);
            g2.dispose();
        }
    }

    private JPanel createDashboardPage() {
        JPanel page = new JPanel(new BorderLayout(16,16));
        page.setOpaque(false);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel welcome = new JLabel("Welcome to Pet Adoption Center");
        welcome.setFont(uiFont(700, 28));
        welcome.setForeground(DEEP_BURGUNDY);
        JLabel subtitle = new JLabel("Every adoption creates a new beginning.");
        subtitle.setForeground(SECONDARY_TEXT);
        header.add(welcome, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.SOUTH);
        page.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(16,16));
        center.setOpaque(false);
        center.add(createStatsRow(), BorderLayout.NORTH);
        JPanel lower = new JPanel(new GridLayout(1,2,16,0));
        lower.setOpaque(false);
        lower.add(createRecentPetsPanel());
        lower.add(createRecentActivityPanel());
        center.add(lower, BorderLayout.CENTER);
        page.add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(16,0));
        bottom.setOpaque(false);
        bottom.add(createAdoptionOverview(), BorderLayout.WEST);
        bottom.add(createQuickActions(), BorderLayout.CENTER);
        page.add(bottom, BorderLayout.SOUTH);
        return page;
    }

    private JPanel createStatsRow() {
        JPanel row = new JPanel(new GridLayout(1,5,12,0));
        row.setOpaque(false);
        List<Pet> pets = petManager.getAllPets();
        long available = pets.stream().filter(p -> "Available".equalsIgnoreCase(p.getAdoptionStatus())).count();
        long adopted = pets.stream().filter(p -> "Adopted".equalsIgnoreCase(p.getAdoptionStatus())).count();
        long pending = adoptionManager.getAllRequests().stream().filter(r -> "Pending".equalsIgnoreCase(r.getStatus())).count();
        row.add(statCard("Total Pets", String.valueOf(pets.size()), "paw", BURGUNDY));
        row.add(statCard("Available Pets", String.valueOf(available), "home", SUCCESS));
        row.add(statCard("Adopted Pets", String.valueOf(adopted), "heart", SECONDARY_BURGUNDY));
        row.add(statCard("Total Adopters", String.valueOf(adopterManager.getAllAdopters().size()), "people", ROSE_GOLD));
        row.add(statCard("Pending Requests", String.valueOf(pending), "clock", WARNING));
        return row;
    }

    private JPanel statCard(String label, String value, String icon, Color accent) {
        JPanel card = roundedPanel();
        card.setLayout(new BorderLayout());
        card.setBorder(new CompoundBorder(new LineBorder(BORDER,1,true), new EmptyBorder(14,14,14,14)));
        addHoverEffect(card);
        Color iconColor = accent.getRed() > 200 ? BURGUNDY : accent;
        JLabel iconLabel = new JLabel(statIcon(icon, iconColor));
        iconLabel.setOpaque(true);
        iconLabel.setBackground(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 35));
        iconLabel.setBorder(new EmptyBorder(8,10,8,10));
        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(uiFont(800, 25));
        valueLabel.setForeground(DARK_TEXT);
        JLabel labelLabel = new JLabel(label);
        labelLabel.setForeground(SECONDARY_TEXT);
        JPanel text = new JPanel(new GridLayout(2,1));
        text.setOpaque(false);
        text.add(valueLabel); text.add(labelLabel);
        card.add(iconLabel, BorderLayout.WEST); card.add(text, BorderLayout.CENTER);
        return card;
    }

    private JPanel roundedPanel() {
        return new RoundedPanel(22, Color.WHITE);
    }

    private JPanel createRecentPetsPanel() {
        JPanel panel = sectionPanel("Recently Added Pets");
        panel.setLayout(new BorderLayout(8,8));
        JPanel list = new JPanel(new GridLayout(1,3,8,0));
        list.setOpaque(false);
        List<Pet> pets = petManager.getAllPets();
        int start = Math.max(0, pets.size()-3);
        for (int i=pets.size()-1; i>=start; i--) list.add(petMiniCard(pets.get(i)));
        panel.add(list, BorderLayout.CENTER);
        return panel;
    }

    private JPanel petMiniCard(Pet pet) {
        JPanel card = new JPanel(new BorderLayout(6,0));
        card.setBackground(BEIGE);
        card.setBorder(new EmptyBorder(7,7,7,7));
        addHoverEffect(card);
        JLabel image = imageLabel(pet.getImagePath(), 72,72);
        JPanel text = new JPanel(new GridLayout(3,1));
        text.setOpaque(false);
        text.add(boldLabel(pet.getName()));
        JLabel breed = new JLabel(pet.getBreed());
        breed.setForeground(DARK_TEXT);
        JLabel status = new JLabel(pet.getAge()+" years • "+pet.getAdoptionStatus());
        status.setForeground(SECONDARY_TEXT);
        text.add(breed);
        text.add(status);
        card.add(image,BorderLayout.WEST); card.add(text,BorderLayout.CENTER);
        return card;
    }

    private JPanel createRecentActivityPanel() {
        JPanel panel = sectionPanel("Recent Adoption Activities");
        panel.setLayout(new BorderLayout());
        String[] columns={"Pet","Adopter","Date","Status"};
        DefaultTableModel model=new DefaultTableModel(columns,0){public boolean isCellEditable(int r,int c){return false;}};
        for (AdoptionRecord r: adoptionManager.getAdoptionRecords()) {
            Pet pet=petManager.findById(r.getPetId());
            Adopter a=adopterManager.findById(r.getAdopterId());
            model.addRow(new Object[]{pet==null?r.getPetId():pet.getName(),a==null?r.getAdopterId():a.getName(),r.getAdoptionDate(),"✓ "+r.getStatus()});
        }
        JTable table=table(model);
        panel.add(new JScrollPane(table),BorderLayout.CENTER);
        return panel;
    }

    private JPanel createAdoptionOverview() {
        JPanel panel = sectionPanel("Adoption Overview");
        panel.setPreferredSize(new Dimension(430,145));
        JPanel stats = new JPanel(new GridLayout(2,2,8,8));
        stats.setOpaque(false);
        stats.add(overviewItem("Adoptions",String.valueOf(adoptionManager.getAdoptionRecords().size()),SUCCESS));
        stats.add(overviewItem("Available",String.valueOf(petManager.getAllPets().stream().filter(p->"Available".equalsIgnoreCase(p.getAdoptionStatus())).count()),BURGUNDY));
        stats.add(overviewItem("Pending",String.valueOf(adoptionManager.getAllRequests().stream().filter(r->"Pending".equalsIgnoreCase(r.getStatus())).count()),WARNING));
        stats.add(overviewItem("Rejected",String.valueOf(adoptionManager.getAllRequests().stream().filter(r->"Rejected".equalsIgnoreCase(r.getStatus())).count()),SECONDARY_BURGUNDY));
        panel.add(stats,BorderLayout.CENTER);
        return panel;
    }

    private JPanel overviewItem(String label,String value,Color color){
        JPanel p=new JPanel(new BorderLayout());
        p.setOpaque(true); p.setBackground(Color.WHITE);
        p.setBorder(new EmptyBorder(8,12,8,12));
        JLabel v=new JLabel(value); v.setForeground(color); v.setFont(uiFont(700, 20));
        JLabel l=new JLabel(label); l.setForeground(SECONDARY_TEXT);
        p.add(v,BorderLayout.NORTH); p.add(l,BorderLayout.SOUTH); return p;
    }

    private JPanel createQuickActions() {
        JPanel panel = sectionPanel("Quick Actions");
        JPanel actions = new JPanel(new GridLayout(0, 2, 10, 9));
        actions.setOpaque(false);
        actions.add(quickActionButton("Add New Pet", new QuickActionIcon("add", BURGUNDY), ()->showPage("Add New Pet")));
        actions.add(quickActionButton("Manage Pets", new PawIcon(20, BURGUNDY), ()->showPage("Pet Management")));
        actions.add(quickActionButton("View Adoption Requests", new QuickActionIcon("heart", BURGUNDY), ()->showPage("Adoption Requests")));
        actions.add(quickActionButton("Manage Adopters", new QuickActionIcon("people", BURGUNDY), ()->showPage("Adopter Management")));
        actions.add(quickActionButton("View Adoption Records", new QuickActionIcon("records", BURGUNDY), ()->showPage("Adoption Records")));
        actions.add(quickActionButton("Register Adopter", new QuickActionIcon("person-add", BURGUNDY), ()->openAdopterDialog(null)));
        panel.add(actions,BorderLayout.CENTER);
        return panel;
    }

    private JButton quickActionButton(String text, Icon icon, Runnable action) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fill = getModel().isPressed() ? ROSE_GOLD
                        : getModel().isRollover() ? ROSE_GOLD : LIGHT_BURGUNDY;
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
                super.paintComponent(graphics);
            }
        };
        button.setForeground(BURGUNDY);
        button.setFont(uiFont(600, 14));
        button.setIcon(icon);
        button.setIconTextGap(10);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setBorder(new EmptyBorder(10, 14, 10, 12));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(e -> action.run());
        return button;
    }

    private JPanel petGrid;
    private JTextField petSearchField;

    private JPanel createPetPage() {
        JPanel page = new JPanel(new BorderLayout(12,12));
        page.setOpaque(false);
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT,10,0));
        toolbar.setOpaque(false);
        petSearchField = new JTextField(22);
        petSearchField.putClientProperty("JTextField.placeholderText","Search pets...");
        JComboBox<String> typeFilter = new JComboBox<>(new String[]{"All Types","Dog","Cat","Rabbit","Bird","Other"});
        JComboBox<String> genderFilter = new JComboBox<>(new String[]{"All Genders","Male","Female"});
        JComboBox<String> statusFilter = new JComboBox<>(new String[]{"All Statuses","Available","Adopted"});
        toolbar.add(new JLabel("Search")); toolbar.add(petSearchField);
        toolbar.add(typeFilter); toolbar.add(genderFilter); toolbar.add(statusFilter);
        toolbar.add(primaryButton("＋ Add Pet", ()->showPage("Add New Pet")));
        petGrid = new JPanel(new GridLayout(0,3,14,14));
        petGrid.setOpaque(false);
        JScrollPane scroll = new JScrollPane(petGrid);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        page.add(toolbar,BorderLayout.NORTH); page.add(scroll,BorderLayout.CENTER);
        ActionListener filter = e -> refreshPetPages(typeFilter.getSelectedItem().toString(),
                genderFilter.getSelectedItem().toString(), statusFilter.getSelectedItem().toString());
        petSearchDebounce = new javax.swing.Timer(120, e -> filter.actionPerformed(null));
        petSearchDebounce.setRepeats(false);
        petSearchField.addKeyListener(new KeyAdapter(){public void keyReleased(KeyEvent e){petSearchDebounce.restart();}});
        typeFilter.addActionListener(filter); genderFilter.addActionListener(filter); statusFilter.addActionListener(filter);
        return page;
    }

    private void refreshPetPages() { refreshPetPages("All Types","All Genders","All Statuses"); }
    private void refreshPetPages(String type,String gender,String status) {
        if (petGrid == null) return;
        petGrid.removeAll();
        String term = petSearchField == null ? "" : petSearchField.getText();
        List<Pet> pets = petManager.searchPet(term);
        int displayed = 0;
        for (Pet pet : pets) {
            if (!type.equals("All Types") && !pet.getType().equals(type)) continue;
            if (!gender.equals("All Genders") && !pet.getGender().equals(gender)) continue;
            if (!status.equals("All Statuses") && !pet.getAdoptionStatus().equals(status)) continue;
            petGrid.add(petCard(pet));
            displayed++;
        }
        if (displayed == 0) petGrid.add(emptyState("No pets match your filters."));
        if (blankCursor != null) installPawCursorListeners(petGrid, getLayeredPane());
        petGrid.revalidate(); petGrid.repaint();
    }

    private JPanel petCard(Pet pet) {
        JPanel card=roundedPanel(); card.setLayout(new BorderLayout(8,8));
        card.setBorder(new CompoundBorder(new LineBorder(BORDER,1,true),new EmptyBorder(10,10,10,10)));
        JLabel image=imageLabel(pet.getImagePath(),130,110);
        card.add(image,BorderLayout.NORTH);
        JPanel info=new JPanel(new BorderLayout());
        info.setOpaque(false);
        JLabel name=boldLabel(pet.getName());
        name.setFont(uiFont(700, 19));
        JLabel breed=new JLabel(pet.getBreed()+" • "+pet.getAge()+" years");
        breed.setForeground(SECONDARY_TEXT);
        JPanel titles=new JPanel(new BorderLayout()); titles.setOpaque(false);
        titles.add(name,BorderLayout.NORTH); titles.add(breed,BorderLayout.CENTER);
        JLabel badge=new JLabel(pet.getAdoptionStatus());
        badge.setOpaque(true); badge.setForeground(DARK_TEXT);
        badge.setBackground("Available".equals(pet.getAdoptionStatus())?new Color(220,238,225):LIGHT_PINK);
        badge.setBorder(new EmptyBorder(4,8,4,8));
        titles.add(badge,BorderLayout.EAST);
        info.add(titles,BorderLayout.NORTH);
        JLabel health=new JLabel(pet.getGender()+" • "+pet.getHealthStatus());
        health.setBorder(new EmptyBorder(8,0,8,0));
        info.add(health,BorderLayout.CENTER);
        JPanel actions=new JPanel(new GridLayout(1,3,5,0)); actions.setOpaque(false);
        actions.add(smallButton("View",()->showPetDetails(pet)));
        actions.add(smallButton("Edit",()->openPetDialog(pet)));
        actions.add(smallButton("Delete",()->deletePet(pet)));
        info.add(actions,BorderLayout.SOUTH);
        card.add(info,BorderLayout.CENTER);
        return card;
    }

    private JPanel createAddPetPage() {
        JPanel page= new JPanel(new BorderLayout(12,12)); page.setOpaque(false);
        JPanel form=roundedPanel(); form.setLayout(new GridBagLayout());
        form.setBorder(new CompoundBorder(new LineBorder(BORDER,1,true),new EmptyBorder(20,20,20,20)));
        GridBagConstraints g=new GridBagConstraints(); g.insets=new Insets(7,7,7,7); g.fill=GridBagConstraints.HORIZONTAL;
        JTextField name=new JTextField(16),breed=new JTextField(16),age=new JTextField(16),colour=new JTextField(16);
        JComboBox<String> type=new JComboBox<>(new String[]{"Dog","Cat","Rabbit","Bird","Other"});
        JComboBox<String> gender=new JComboBox<>(new String[]{"Male","Female"});
        JComboBox<String> health=new JComboBox<>(new String[]{"Healthy","Under Observation","In Treatment"});
        JComboBox<String> vaccine=new JComboBox<>(new String[]{"Fully Vaccinated","Partially Vaccinated","Not Vaccinated","Not Required"});
        JTextArea description=new JTextArea(4,20); description.setLineWrap(true); description.setWrapStyleWord(true);
        JTextField imagePath=new JTextField(16); imagePath.setEditable(false);
        JButton upload=new JButton("Choose Photograph");
        upload.addActionListener(e->chooseImage(imagePath));
        int r=0;
        addFormRow(form,g,r++,"Pet Name",name,"Animal Type",type);
        addFormRow(form,g,r++,"Breed",breed,"Age (years)",age);
        addFormRow(form,g,r++,"Gender",gender,"Colour",colour);
        addFormRow(form,g,r++,"Health Status",health,"Vaccination Status",vaccine);
        g.gridx=0;g.gridy=r;g.gridwidth=1;form.add(new JLabel("Description"),g);
        g.gridx=1;g.gridy=r;g.gridwidth=3;form.add(new JScrollPane(description),g);r++;
        g.gridx=0;g.gridy=r;g.gridwidth=1;form.add(new JLabel("Pet Photograph"),g);
        g.gridx=1;g.gridy=r;g.gridwidth=2;form.add(imagePath,g);
        g.gridx=3;g.gridy=r;g.gridwidth=1;form.add(upload,g);r++;
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT)); actions.setOpaque(false);
        JButton save=primaryButton("Save Pet",()->{
            try{
                validateRequired(name.getText(),"Pet name"); validateRequired(breed.getText(),"Breed");
                int years=Integer.parseInt(age.getText().trim()); if(years<0||years>30)throw new IllegalArgumentException("Age must be between 0 and 30");
                String id=nextId("PET",petManager.getAllPets().size(),petManager.getAllPets(),0);
                String path=imagePath.getText().trim();
                if(path.isEmpty())path="assets/pet_images/luna.jpg";
                Pet pet=new Pet(id,name.getText().trim(),type.getSelectedItem().toString(),breed.getText().trim(),years,
                        gender.getSelectedItem().toString(),colour.getText().trim(),health.getSelectedItem().toString(),
                        vaccine.getSelectedItem().toString(),description.getText().trim(),LocalDate.now().toString(),"Available",path);
                petManager.addPet(pet); showSuccess("Pet "+pet.getName()+" registered successfully.");
                clearPetForm(name,breed,age,colour,description,imagePath);
                showPage("Pet Management");
            }catch(Exception ex){showError(ex.getMessage());}
        });
        JButton clear=secondaryButton("Clear Form",()->clearPetForm(name,breed,age,colour,description,imagePath));
        JButton cancel=secondaryButton("Cancel",()->showPage("Dashboard"));
        actions.add(save);actions.add(clear);actions.add(cancel);
        g.gridx=0;g.gridy=r;g.gridwidth=4;form.add(actions,g);
        page.add(form,BorderLayout.NORTH);
        return page;
    }

    private DefaultTableModel requestTableModel;
    private DefaultTableModel adopterTableModel;
    private DefaultTableModel recordTableModel;

    private JPanel createRequestsPage() {
        JPanel page=new JPanel(new BorderLayout(10,10)); page.setOpaque(false);
        JPanel toolbar=new JPanel(new FlowLayout(FlowLayout.LEFT)); toolbar.setOpaque(false);
        JTextField search=new JTextField(20); JComboBox<String> filter=new JComboBox<>(new String[]{"All","Pending","Approved","Rejected"});
        toolbar.add(new JLabel("Search"));toolbar.add(search);toolbar.add(new JLabel("Status"));toolbar.add(filter);
        JButton submit=primaryButton("＋ Submit Adoption Request",()->openRequestDialog());
        toolbar.add(submit);
        requestTableModel=new DefaultTableModel(new String[]{"Request ID","Adopter","Pet","Date","Status"},0){
            public boolean isCellEditable(int r,int c){return false;}
        };
        JTable table=table(requestTableModel);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));actions.setOpaque(false);
        JButton approve=successButton("Approve Selected",()->approveSelectedRequest(table));
        JButton reject=secondaryButton("Reject Selected",()->rejectSelectedRequest(table));
        JButton details=secondaryButton("View Details",()->viewRequestDetails(table));
        actions.add(details);actions.add(reject);actions.add(approve);
        page.add(toolbar,BorderLayout.NORTH);
        JScrollPane scroll=new JScrollPane(table);scroll.setBorder(new LineBorder(BORDER,1,true));
        page.add(scroll,BorderLayout.CENTER);page.add(actions,BorderLayout.SOUTH);
        Runnable refresh=()->refreshRequestPage(search.getText(),filter.getSelectedItem().toString());
        search.addKeyListener(new KeyAdapter(){public void keyReleased(KeyEvent e){refresh.run();}});
        filter.addActionListener(e->refresh.run());
        refreshRequestPage();
        return page;
    }

    private void refreshRequestPage(){refreshRequestPage("","All");}
    private void refreshRequestPage(String term,String status){
        if(requestTableModel==null)return;
        requestTableModel.setRowCount(0);
        String needle=term.toLowerCase();
        for(AdoptionRequest r:adoptionManager.getAllRequests()){
            if(!status.equals("All")&&!r.getStatus().equals(status))continue;
            Pet pet=petManager.findById(r.getPetId());
            Adopter adopter=adopterManager.findById(r.getAdopterId());
            String text=(r.getRequestId()+" "+pet.getName()+" "+adopter.getName()).toLowerCase();
            if(!needle.isEmpty()&&!text.contains(needle))continue;
            requestTableModel.addRow(new Object[]{r.getRequestId(),adopter.getName(),pet.getName(),r.getRequestDate(),r.getStatus()});
        }
        requestTableModel.fireTableDataChanged();
    }

    private JPanel createAdoptersPage(){
        JPanel page=new JPanel(new BorderLayout(10,10));page.setOpaque(false);
        JPanel toolbar=new JPanel(new FlowLayout(FlowLayout.LEFT));toolbar.setOpaque(false);
        JTextField search=new JTextField(20);toolbar.add(new JLabel("Search"));
        toolbar.add(search);toolbar.add(primaryButton("＋ Register Adopter",()->openAdopterDialog(null)));
        adopterTableModel=new DefaultTableModel(new String[]{"Adopter ID","Name","Age","Gender","Phone","Email"},0){
            public boolean isCellEditable(int r,int c){return false;}
        };
        JTable table=table(adopterTableModel);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));actions.setOpaque(false);
        actions.add(smallButton("Edit",()->editSelectedAdopter(table)));
        actions.add(smallButton("Delete",()->deleteSelectedAdopter(table)));
        actions.add(smallButton("View History",()->viewAdopterHistory(table)));
        page.add(toolbar,BorderLayout.NORTH);page.add(new JScrollPane(table),BorderLayout.CENTER);page.add(actions,BorderLayout.SOUTH);
        search.addKeyListener(new KeyAdapter(){public void keyReleased(KeyEvent e){
            adopterTableModel.setRowCount(0);
            for(Adopter a:adopterManager.searchAdopter(search.getText())){
                adopterTableModel.addRow(new Object[]{a.getAdopterId(),a.getName(),a.getAge(),a.getGender(),a.getPhone(),a.getEmail()});
            }
        }});
        refreshAdopterPage();
        return page;
    }

    private void refreshAdopterPage(){
        if(adopterTableModel==null)return;
        adopterTableModel.setRowCount(0);
        for(Adopter a:adopterManager.getAllAdopters()){
            adopterTableModel.addRow(new Object[]{a.getAdopterId(),a.getName(),a.getAge(),a.getGender(),a.getPhone(),a.getEmail()});
        }
    }

    private JPanel createRecordsPage(){
        JPanel page=new JPanel(new BorderLayout(10,10));page.setOpaque(false);
        JPanel toolbar=new JPanel(new FlowLayout(FlowLayout.LEFT));toolbar.setOpaque(false);
        JTextField petSearch=new JTextField(16),adopterSearch=new JTextField(16);
        toolbar.add(new JLabel("Pet"));toolbar.add(petSearch);toolbar.add(new JLabel("Adopter"));toolbar.add(adopterSearch);
        recordTableModel=new DefaultTableModel(new String[]{"Adoption ID","Pet","Adopter","Date","Status"},0){
            public boolean isCellEditable(int r,int c){return false;}
        };
        JTable table=table(recordTableModel);
        page.add(toolbar,BorderLayout.NORTH);page.add(new JScrollPane(table),BorderLayout.CENTER);
        Runnable refresh=()->refreshRecordPage(petSearch.getText(),adopterSearch.getText());
        petSearch.addKeyListener(new KeyAdapter(){public void keyReleased(KeyEvent e){refresh.run();}});
        adopterSearch.addKeyListener(new KeyAdapter(){public void keyReleased(KeyEvent e){refresh.run();}});
        refreshRecordPage();
        return page;
    }

    private void refreshRecordPage(){refreshRecordPage("","");}
    private void refreshRecordPage(String petTerm,String adopterTerm){
        if(recordTableModel==null)return;
        recordTableModel.setRowCount(0);
        for(AdoptionRecord r:adoptionManager.getAdoptionRecords()){
            Pet pet=petManager.findById(r.getPetId());Adopter a=adopterManager.findById(r.getAdopterId());
            String pn=pet==null?r.getPetId():pet.getName();String an=a==null?r.getAdopterId():a.getName();
            if(!petTerm.isEmpty()&&!pn.toLowerCase().contains(petTerm.toLowerCase()))continue;
            if(!adopterTerm.isEmpty()&&!an.toLowerCase().contains(adopterTerm.toLowerCase()))continue;
            recordTableModel.addRow(new Object[]{r.getAdoptionId(),pn,an,r.getAdoptionDate(),"✓ "+r.getStatus()});
        }
    }

    private JPanel reportMetrics;

    private JPanel createReportsPage(){
        JPanel page=new JPanel(new BorderLayout(12,12));page.setOpaque(false);
        reportMetrics=new JPanel(new GridLayout(2,4,10,10));reportMetrics.setOpaque(false);
        page.add(reportMetrics,BorderLayout.NORTH);
        JPanel chartPanel=new JPanel(new BorderLayout());chartPanel.setOpaque(false);
        chartPanel.add(new BarChartPanel(),BorderLayout.CENTER);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));actions.setOpaque(false);
        actions.add(primaryButton("Export Adoption Report (CSV)",this::exportReport));
        chartPanel.add(actions,BorderLayout.SOUTH);
        page.add(chartPanel,BorderLayout.CENTER);
        refreshReportsPage();
        return page;
    }

    private void refreshReportsPage(){
        if(reportMetrics==null)return;
        reportMetrics.removeAll();
        long available=petManager.getAllPets().stream().filter(p->"Available".equalsIgnoreCase(p.getAdoptionStatus())).count();
        long adopted=petManager.getAllPets().stream().filter(p->"Adopted".equalsIgnoreCase(p.getAdoptionStatus())).count();
        long pending=adoptionManager.getAllRequests().stream().filter(r->"Pending".equalsIgnoreCase(r.getStatus())).count();
        long rejected=adoptionManager.getAllRequests().stream().filter(r->"Rejected".equalsIgnoreCase(r.getStatus())).count();
        double success=petManager.getAllPets().isEmpty()?0:(adopted/(double)petManager.getAllPets().size()*100);
        reportMetrics.add(statCard("Total Pets",String.valueOf(petManager.getAllPets().size()),"paw",BURGUNDY));
        reportMetrics.add(statCard("Available",String.valueOf(available),"home",SUCCESS));
        reportMetrics.add(statCard("Adopted",String.valueOf(adopted),"heart",SECONDARY_BURGUNDY));
        reportMetrics.add(statCard("Adopters",String.valueOf(adopterManager.getAllAdopters().size()),"people",ROSE_GOLD));
        reportMetrics.add(statCard("Pending",String.valueOf(pending),"clock",WARNING));
        reportMetrics.add(statCard("Rejected",String.valueOf(rejected),"cross",SECONDARY_BURGUNDY));
        reportMetrics.add(statCard("Success Rate",String.format("%.1f%%",success),"chart",SUCCESS));
        reportMetrics.revalidate();reportMetrics.repaint();
    }

    private void exportReport(){
        List<String[]> rows=new ArrayList<>();
        rows.add(new String[]{"Metric","Value"});
        rows.add(new String[]{"Total registered pets",String.valueOf(petManager.getAllPets().size())});
        rows.add(new String[]{"Available pets",String.valueOf(petManager.getAllPets().stream().filter(p->"Available".equalsIgnoreCase(p.getAdoptionStatus())).count())});
        rows.add(new String[]{"Adopted pets",String.valueOf(petManager.getAllPets().stream().filter(p->"Adopted".equalsIgnoreCase(p.getAdoptionStatus())).count())});
        rows.add(new String[]{"Registered adopters",String.valueOf(adopterManager.getAllAdopters().size())});
        rows.add(new String[]{"Pending requests",String.valueOf(adoptionManager.getAllRequests().stream().filter(r->"Pending".equalsIgnoreCase(r.getStatus())).count())});
        rows.add(new String[]{"Rejected requests",String.valueOf(adoptionManager.getAllRequests().stream().filter(r->"Rejected".equalsIgnoreCase(r.getStatus())).count())});
        String stamp=LocalDate.now().toString();
        fileManager.exportReport(rows,"adoption_report_"+stamp+".csv");
        showSuccess("Report exported to "+fileManager.getDataDirectory());
    }

    private JPanel createAboutPage(){
        JPanel page=new JPanel(new BorderLayout(16,16));page.setOpaque(false);
        JPanel left=roundedPanel();left.setLayout(new BorderLayout());left.setBorder(new EmptyBorder(24,24,24,24));
        JLabel title=boldLabel("Pet Adoption Center Manager");
        title.setFont(uiFont(800,28));title.setForeground(DEEP_BURGUNDY);
        JLabel tagline=new JLabel("Every Pet Deserves a Loving Home.");
        tagline.setForeground(SECONDARY_BURGUNDY);
        JPanel heading=new JPanel(new GridLayout(2,1));heading.setOpaque(false);heading.add(title);heading.add(tagline);left.add(heading,BorderLayout.NORTH);
        JTextArea intro=new JTextArea("The Pet Adoption Center Manager simplifies pet registration, adopter records, adoption requests and adoption history for an animal welfare organisation.");
        intro.setWrapStyleWord(true);intro.setLineWrap(true);intro.setEditable(false);intro.setOpaque(false);
        intro.setFont(uiFont(400,15));intro.setForeground(DARK_TEXT);intro.setBorder(new EmptyBorder(15,0,15,0));
        JTextArea features=new JTextArea("Features\n• Pet and adopter CRUD\n• Adoption approval workflow\n• Persistent CSV storage\n• Search and filters\n• Reports and CSV export");
        features.setEditable(false);features.setOpaque(false);features.setForeground(DARK_TEXT);features.setFont(uiFont(400,14));
        left.add(intro,BorderLayout.CENTER);left.add(features,BorderLayout.SOUTH);
        JPanel right=roundedPanel();right.setLayout(new GridLayout(0,1,10,10));right.setBorder(new EmptyBorder(20,20,20,20));
        right.add(new JLabel("<html><b>Technologies</b><br>Java 17 • Java Swing • CSV file handling</html>"));
        right.add(new JLabel("<html><b>Java Concepts</b><br>OOP • Encapsulation • Collections • Exceptions • File Handling</html>"));
        right.add(new JLabel("<html><b>Team Members</b><br>Faruq Hassan — 4BP25AD011<br>Sheikh Mohammed Zain — 4BP25AD054<br>Shammaz Ahmad — 4BP25AD052</html>"));
        right.add(new JLabel("<html><b>College Information</b><br>College: Bearys Institute of Technology<br>Department: Artificial Intelligence and Data Science<br>Subject: OOPJ</html>"));
        page.add(left,BorderLayout.CENTER);page.add(right,BorderLayout.EAST);
        return page;
    }

    private class BarChartPanel extends JPanel {
        BarChartPanel(){setBackground(Color.WHITE);setBorder(new CompoundBorder(new LineBorder(BORDER,1,true),new EmptyBorder(20,20,20,20)));setPreferredSize(new Dimension(700,300));}
        protected void paintComponent(Graphics g){
            super.paintComponent(g);
            Graphics2D g2=(Graphics2D)g.create();g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();
            Map<String,Integer> values=new LinkedHashMap<>();
            values.put("Pets",petManager.getAllPets().size());
            values.put("Available", (int)petManager.getAllPets().stream().filter(p->"Available".equalsIgnoreCase(p.getAdoptionStatus())).count());
            values.put("Adopted",(int)petManager.getAllPets().stream().filter(p->"Adopted".equalsIgnoreCase(p.getAdoptionStatus())).count());
            values.put("Adopters",adopterManager.getAllAdopters().size());
            values.put("Pending",(int)adoptionManager.getAllRequests().stream().filter(r->"Pending".equalsIgnoreCase(r.getStatus())).count());
            values.put("Rejected",(int)adoptionManager.getAllRequests().stream().filter(r->"Rejected".equalsIgnoreCase(r.getStatus())).count());
            int max=values.values().stream().mapToInt(Integer::intValue).max().orElse(1);
            int base=h-55,chartW=w-80,slot=chartW/values.size();
            g2.setFont(uiFont(700,13));
            int i=0;
            for(Map.Entry<String,Integer> entry:values.entrySet()){
                int barH=(int)((h-100)*(entry.getValue()/ (double)Math.max(max,1)));
                int x=45+i*slot+(slot-55)/2;
                g2.setColor(i%2==0?BURGUNDY:ROSE_GOLD);
                g2.fillRoundRect(x,base-barH,55,barH,12,12);
                g2.setColor(DARK_TEXT);g2.drawString(String.valueOf(entry.getValue()),x+22,base-barH-8);
                g2.drawString(entry.getKey(),x-5,base+20);
                i++;
            }
            g2.dispose();
        }
    }

    private void openPetDialog(Pet pet) {
        JDialog dialog = new JDialog(this, "Update Pet", true);
        dialog.setSize(620, 520);
        dialog.setLocationRelativeTo(this);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(16,16,16,16));
        form.setBackground(CREAM);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(7,7,7,7); g.fill = GridBagConstraints.HORIZONTAL;
        JTextField name=new JTextField(pet.getName(),16), breed=new JTextField(pet.getBreed(),16),
                age=new JTextField(String.valueOf(pet.getAge()),16), colour=new JTextField(pet.getColour(),16);
        JTextField imagePath=new JTextField(pet.getImagePath(),16);
        JComboBox<String> type=new JComboBox<>(new String[]{"Dog","Cat","Rabbit","Bird","Other"});
        type.setSelectedItem(pet.getType());
        JComboBox<String> gender=new JComboBox<>(new String[]{"Male","Female"}); gender.setSelectedItem(pet.getGender());
        JComboBox<String> health=new JComboBox<>(new String[]{"Healthy","Under Observation","In Treatment"});
        health.setSelectedItem(pet.getHealthStatus());
        JComboBox<String> vaccine=new JComboBox<>(new String[]{"Fully Vaccinated","Partially Vaccinated","Not Vaccinated","Not Required"});
        vaccine.setSelectedItem(pet.getVaccinationStatus());
        JTextArea description=new JTextArea(pet.getDescription(),4,20); description.setLineWrap(true); description.setWrapStyleWord(true);
        int r=0;
        addFormRow(form,g,r++,"Pet Name",name,"Animal Type",type);
        addFormRow(form,g,r++,"Breed",breed,"Age",age);
        addFormRow(form,g,r++,"Gender",gender,"Colour",colour);
        addFormRow(form,g,r++,"Health",health,"Vaccination",vaccine);
        g.gridx=0;g.gridy=r;g.gridwidth=1;form.add(new JLabel("Description"),g);
        g.gridx=1;g.gridy=r;g.gridwidth=3;form.add(new JScrollPane(description),g);r++;
        g.gridx=0;g.gridy=r;g.gridwidth=1;form.add(new JLabel("Image Path"),g);
        g.gridx=1;g.gridy=r;g.gridwidth=3;form.add(imagePath,g);r++;
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));actions.setOpaque(false);
        JButton save=primaryButton("Save Changes",()->{
            try {
                validateRequired(name.getText(),"Pet name"); validateRequired(breed.getText(),"Breed");
                int years=Integer.parseInt(age.getText().trim());
                if(years<0||years>30)throw new IllegalArgumentException("Age must be between 0 and 30");
                pet.setName(name.getText().trim());pet.setType(type.getSelectedItem().toString());
                pet.setBreed(breed.getText().trim());pet.setAge(years);pet.setGender(gender.getSelectedItem().toString());
                pet.setColour(colour.getText().trim());pet.setHealthStatus(health.getSelectedItem().toString());
                pet.setVaccinationStatus(vaccine.getSelectedItem().toString());pet.setDescription(description.getText().trim());
                pet.setImagePath(imagePath.getText().trim());
                petManager.updatePet(pet);dialog.dispose();refreshPetPages();showSuccess("Pet details updated.");
            } catch(Exception ex){showError(ex.getMessage());}
        });
        actions.add(save);actions.add(secondaryButton("Cancel",dialog::dispose));
        g.gridx=0;g.gridy=r;g.gridwidth=4;form.add(actions,g);
        dialog.add(form);dialog.setVisible(true);
    }

    private void deletePet(Pet pet) {
        int answer=JOptionPane.showConfirmDialog(this,"Delete "+pet.getName()+" permanently?","Confirm Delete",
                JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE);
        if(answer==JOptionPane.YES_OPTION){
            try{petManager.deletePet(pet.getPetId());refreshPetPages();showSuccess("Pet deleted.");}
            catch(Exception ex){showError(ex.getMessage());}
        }
    }

    private void showPetDetails(Pet pet) {
        JOptionPane.showMessageDialog(this,
                "<html><b>"+pet.getName()+"</b><br>"+
                "ID: "+pet.getPetId()+"<br>Type: "+pet.getType()+"<br>Breed: "+pet.getBreed()+"<br>"+
                "Age: "+pet.getAge()+" years<br>Gender: "+pet.getGender()+"<br>Colour: "+pet.getColour()+"<br>"+
                "Health: "+pet.getHealthStatus()+"<br>Vaccination: "+pet.getVaccinationStatus()+"<br>"+
                "Registered: "+pet.getRegistrationDate()+"<br>Status: "+pet.getAdoptionStatus()+"<br>"+
                "<br>"+pet.getDescription()+"</html>",
                "Pet Details",JOptionPane.INFORMATION_MESSAGE);
    }

    private void openAdopterDialog(Adopter adopter) {
        boolean editing=adopter!=null;
        JDialog dialog=new JDialog(this,editing?"Update Adopter":"Register Adopter",true);
        dialog.setSize(560,460);dialog.setLocationRelativeTo(this);
        JPanel form=new JPanel(new GridBagLayout());form.setBorder(new EmptyBorder(16,16,16,16));form.setBackground(CREAM);
        GridBagConstraints g=new GridBagConstraints();g.insets=new Insets(7,7,7,7);g.fill=GridBagConstraints.HORIZONTAL;
        JTextField name=new JTextField(editing?adopter.getName():"",16);
        JTextField age=new JTextField(editing?String.valueOf(adopter.getAge()):"",16);
        JComboBox<String> gender=new JComboBox<>(new String[]{"Male","Female"});if(editing)gender.setSelectedItem(adopter.getGender());
        JTextField phone=new JTextField(editing?adopter.getPhone():"",16);
        JTextField email=new JTextField(editing?adopter.getEmail():"",16);
        JTextArea address=new JTextArea(editing?adopter.getAddress(): "",3,20);address.setLineWrap(true);address.setWrapStyleWord(true);
        int r=0;addFormRow(form,g,r++,"Full Name",name,"Age",age);
        addFormRow(form,g,r++,"Gender",gender,"Phone",phone);
        g.gridx=0;g.gridy=r;g.gridwidth=1;form.add(new JLabel("Email"),g);
        g.gridx=1;g.gridy=r;g.gridwidth=3;form.add(email,g);r++;
        g.gridx=0;g.gridy=r;g.gridwidth=1;form.add(new JLabel("Address"),g);
        g.gridx=1;g.gridy=r;g.gridwidth=3;form.add(new JScrollPane(address),g);r++;
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));actions.setOpaque(false);
        JButton save=primaryButton(editing?"Save Changes":"Register",()->{
            try{
                validateRequired(name.getText(),"Full name");validateRequired(phone.getText(),"Phone");validateRequired(email.getText(),"Email");validateRequired(address.getText(),"Address");
                int years=Integer.parseInt(age.getText().trim());if(years<18||years>100)throw new IllegalArgumentException("Adopter age must be between 18 and 100");
                if(!phone.getText().trim().matches("[0-9()+ -]{7,15}"))throw new IllegalArgumentException("Enter a valid phone number");
                if(!email.getText().trim().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))throw new IllegalArgumentException("Enter a valid email address");
                if(editing){
                    adopter.setName(name.getText().trim());adopter.setAge(years);adopter.setGender(gender.getSelectedItem().toString());
                    adopter.setPhone(phone.getText().trim());adopter.setEmail(email.getText().trim());adopter.setAddress(address.getText().trim());
                    adopterManager.updateAdopter(adopter);
                }else{
                    String id=nextAdopterId();
                    adopterManager.addAdopter(new Adopter(id,name.getText().trim(),years,gender.getSelectedItem().toString(),
                            phone.getText().trim(),email.getText().trim(),address.getText().trim(),LocalDate.now().toString()));
                }
                dialog.dispose();refreshAdopterPage();showSuccess(editing?"Adopter updated.":"Adopter registered successfully.");
            }catch(Exception ex){showError(ex.getMessage());}
        });
        actions.add(save);actions.add(secondaryButton("Cancel",dialog::dispose));
        g.gridx=0;g.gridy=r;g.gridwidth=4;form.add(actions,g);
        dialog.add(form);dialog.setVisible(true);
    }

    private void openRequestDialog(){
        JDialog dialog=new JDialog(this,"Submit Adoption Request",true);
        dialog.setSize(560,500);dialog.setLocationRelativeTo(this);
        JPanel form=new JPanel(new GridBagLayout());form.setBorder(new EmptyBorder(16,16,16,16));form.setBackground(CREAM);
        GridBagConstraints g=new GridBagConstraints();g.insets=new Insets(8,8,8,8);g.fill=GridBagConstraints.HORIZONTAL;
        JComboBox<String> adopterBox=new JComboBox<>();
        Map<String,Adopter> adopters=new HashMap<>();
        for(Adopter a:adopterManager.getAllAdopters()){String key=a.getName()+" ("+a.getAdopterId()+")";adopters.put(key,a);adopterBox.addItem(key);}
        JComboBox<String> petBox=new JComboBox<>();
        Map<String,Pet> pets=new HashMap<>();
        for(Pet p:petManager.getAllPets())if("Available".equalsIgnoreCase(p.getAdoptionStatus())){
            String key=p.getName()+" ("+p.getPetId()+")";pets.put(key,p);petBox.addItem(key);
        }
        JTextArea reason=new JTextArea(4,20);reason.setLineWrap(true);reason.setWrapStyleWord(true);
        g.gridx=0;g.gridy=0;form.add(new JLabel("Adopter"),g);g.gridx=1;g.gridy=0;g.gridwidth=3;form.add(adopterBox,g);
        g.gridx=0;g.gridy=1;g.gridwidth=1;form.add(new JLabel("Available Pet"),g);g.gridx=1;g.gridy=1;g.gridwidth=3;form.add(petBox,g);
        g.gridx=0;g.gridy=2;g.gridwidth=1;form.add(new JLabel("Reason"),g);g.gridx=1;g.gridy=2;g.gridwidth=3;form.add(new JScrollPane(reason),g);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));actions.setOpaque(false);
        JButton submit=primaryButton("Submit Request",()->{
            try{
                if(adopterBox.getSelectedItem()==null||petBox.getSelectedItem()==null)throw new IllegalArgumentException("Select an adopter and available pet");
                validateRequired(reason.getText(),"Reason for adoption");
                Adopter adopter=adopters.get(adopterBox.getSelectedItem().toString());
                Pet pet=pets.get(petBox.getSelectedItem().toString());
                String id=nextRequestId();
                adoptionManager.submitRequest(new AdoptionRequest(id,adopter.getAdopterId(),pet.getPetId(),reason.getText().trim(),LocalDate.now().toString(),"Pending"));
                dialog.dispose();refreshRequestPage();showSuccess("Adoption request submitted.");
            }catch(Exception ex){showError(ex.getMessage());}
        });
        actions.add(submit);actions.add(secondaryButton("Cancel",dialog::dispose));
        g.gridx=0;g.gridy=3;g.gridwidth=4;form.add(actions,g);
        dialog.add(form);dialog.setVisible(true);
    }

    private void approveSelectedRequest(JTable table){
        int row=table.getSelectedRow();
        if(row<0){showError("Select a request first.");return;}
        String id=table.getValueAt(row,0).toString();
        int answer=JOptionPane.showConfirmDialog(this,"Approve adoption request "+id+"?","Confirm Approval",JOptionPane.YES_NO_OPTION);
        if(answer==JOptionPane.YES_OPTION){
            try{
                adoptionManager.approveRequest(id);
                refreshRequestPage();refreshRecordPage();refreshPetPages();showSuccess("Request approved and adoption record created.");
            }catch(Exception ex){showError(ex.getMessage());}
        }
    }

    private void rejectSelectedRequest(JTable table){
        int row=table.getSelectedRow();
        if(row<0){showError("Select a request first.");return;}
        String id=table.getValueAt(row,0).toString();
        int answer=JOptionPane.showConfirmDialog(this,"Reject adoption request "+id+"?","Confirm Rejection",JOptionPane.YES_NO_OPTION);
        if(answer==JOptionPane.YES_OPTION){
            try{adoptionManager.rejectRequest(id);refreshRequestPage();showSuccess("Request rejected.");}
            catch(Exception ex){showError(ex.getMessage());}
        }
    }

    private void viewRequestDetails(JTable table){
        int row=table.getSelectedRow();
        if(row<0){showError("Select a request first.");return;}
        AdoptionRequest r=adoptionManager.findRequest(table.getValueAt(row,0).toString());
        if(r==null)return;
        Pet pet=petManager.findById(r.getPetId());Adopter adopter=adopterManager.findById(r.getAdopterId());
        JOptionPane.showMessageDialog(this,
                "<html><b>Request "+r.getRequestId()+"</b><br>"+
                "Adopter: "+adopter.getName()+" ("+adopter.getAdopterId()+")<br>"+
                "Phone: "+adopter.getPhone()+"<br>Email: "+adopter.getEmail()+"<br>"+
                "Pet: "+pet.getName()+" ("+pet.getPetId()+")<br>Date: "+r.getRequestDate()+"<br>"+
                "Status: "+r.getStatus()+"<br><br>Reason:<br>"+r.getReason()+"</html>",
                "Request Details",JOptionPane.INFORMATION_MESSAGE);
    }

    private void editSelectedAdopter(JTable table){
        int row=table.getSelectedRow();
        if(row<0){showError("Select an adopter first.");return;}
        Adopter adopter=adopterManager.findById(table.getValueAt(row,0).toString());
        if(adopter!=null)openAdopterDialog(adopter);
    }

    private void deleteSelectedAdopter(JTable table){
        int row=table.getSelectedRow();
        if(row<0){showError("Select an adopter first.");return;}
        String id=table.getValueAt(row,0).toString();
        int answer=JOptionPane.showConfirmDialog(this,"Delete adopter "+id+"?","Confirm Delete",JOptionPane.YES_NO_OPTION);
        if(answer==JOptionPane.YES_OPTION){
            try{adopterManager.deleteAdopter(id);refreshAdopterPage();showSuccess("Adopter deleted.");}
            catch(Exception ex){showError(ex.getMessage());}
        }
    }

    private void viewAdopterHistory(JTable table){
        int row=table.getSelectedRow();
        if(row<0){showError("Select an adopter first.");return;}
        String id=table.getValueAt(row,0).toString();
        Adopter adopter=adopterManager.findById(id);
        StringBuilder history=new StringBuilder();
        for(AdoptionRecord r:adoptionManager.getAdoptionRecords())if(r.getAdopterId().equals(id)){
            Pet pet=petManager.findById(r.getPetId());
            history.append(pet.getName()).append(" - ").append(r.getAdoptionDate()).append(" (").append(r.getStatus()).append(")\n");
        }
        JOptionPane.showMessageDialog(this,history.length()==0?"No adoption history found.":history.toString(),
                "Adoption History: "+adopter.getName(),JOptionPane.INFORMATION_MESSAGE);
    }

    private void chooseImage(JTextField imagePath){
        JFileChooser chooser=new JFileChooser();
        chooser.setDialogTitle("Choose Pet Photograph");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Images","jpg","jpeg","png","gif"));
        if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION){
            try{
                Path source=chooser.getSelectedFile().toPath();
                Files.createDirectories(Paths.get("assets","pet_images"));
                String name="pet_"+System.currentTimeMillis()+"_"+source.getFileName();
                Path target=Paths.get("assets","pet_images",name);
                Files.copy(source,target,StandardCopyOption.REPLACE_EXISTING);
                imagePath.setText(target.toString().replace("\\","/"));
            }catch(IOException ex){showError("Could not copy photograph: "+ex.getMessage());}
        }
    }

    private void addFormRow(JPanel panel,GridBagConstraints g,int row,String leftLabel,JComponent left,String rightLabel,JComponent right){
        g.gridx=0;g.gridy=row;g.gridwidth=1;panel.add(new JLabel(leftLabel),g);
        g.gridx=1;g.gridy=row;g.gridwidth=1;panel.add(left,g);
        g.gridx=2;g.gridy=row;g.gridwidth=1;panel.add(new JLabel(rightLabel),g);
        g.gridx=3;g.gridy=row;g.gridwidth=1;panel.add(right,g);
    }

    private void clearPetForm(JTextField name,JTextField breed,JTextField age,JTextField colour,JTextArea description,JTextField imagePath){
        name.setText("");breed.setText("");age.setText("");colour.setText("");description.setText("");imagePath.setText("");
    }

    private void validateRequired(String value,String label){
        if(value==null||value.trim().isEmpty())throw new IllegalArgumentException(label+" is required.");
    }

    private String nextAdopterId(){
        int max=0;for(Adopter a:adopterManager.getAllAdopters()){
            try{max=Math.max(max,Integer.parseInt(a.getAdopterId().replaceAll("[^0-9]","")));}catch(RuntimeException ignored){}
        }return "ADT"+String.format("%03d",max+1);
    }
    private String nextRequestId(){
        int max=0;for(AdoptionRequest r:adoptionManager.getAllRequests()){
            try{max=Math.max(max,Integer.parseInt(r.getRequestId().replaceAll("[^0-9]","")));}catch(RuntimeException ignored){}
        }return "REQ"+String.format("%03d",max+1);
    }
    private String nextId(String prefix,int ignoredSize,List<?> values,int ignored){
        int max=0;for(Object object:values){
            String id="";
            if(object instanceof Pet)id=((Pet)object).getPetId();
            try{max=Math.max(max,Integer.parseInt(id.replaceAll("[^0-9]","")));}catch(RuntimeException ex){}
        }return prefix+String.format("%03d",max+1);
    }

    private JLabel boldLabel(String text){JLabel l=new JLabel(text);l.setFont(uiFont(700,15));l.setForeground(DARK_TEXT);return l;}

    private JPanel sectionPanel(String title){
        JPanel panel=roundedPanel();panel.setLayout(new BorderLayout());panel.setBorder(new CompoundBorder(
                new LineBorder(BORDER,1,true),new EmptyBorder(12,12,12,12)));
        JLabel heading=boldLabel(title);heading.setFont(uiFont(700,22));heading.setBorder(new EmptyBorder(0,0,8,0));
        panel.add(heading,BorderLayout.NORTH);return panel;
    }

    private JLabel imageLabel(String path,int width,int height){
        JLabel label=new JLabel();
        label.setPreferredSize(new Dimension(width,height));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setBorder(new LineBorder(BORDER,1,true));
        label.setOpaque(true);label.setBackground(BEIGE);
        String cacheKey = path + "|" + width + "x" + height;
        ImageIcon cached = imageCache.get(cacheKey);
        if (cached != null) {
            label.setIcon(cached);
            return label;
        }
        try{
            BufferedImage image=sourceImageCache.get(path);
            if(image==null&&!missingImagePaths.contains(path)){
                image=ImageIO.read(new File(path));
                if(image!=null)sourceImageCache.put(path,image);
                else missingImagePaths.add(path);
            }
            if(image!=null){
                int targetW=width,targetH=height;
                double scale=Math.max(targetW/(double)image.getWidth(),targetH/(double)image.getHeight());
                int sw=(int)(image.getWidth()*scale),sh=(int)(image.getHeight()*scale);
                BufferedImage scaled=new BufferedImage(targetW,targetH,BufferedImage.TYPE_INT_RGB);
                Graphics2D g2=scaled.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.drawImage(image,(targetW-sw)/2,(targetH-sh)/2,sw,sh,null);g2.dispose();
                ImageIcon icon = new ImageIcon(scaled);
                imageCache.put(cacheKey, icon);
                label.setIcon(icon);return label;
            }
        }catch(Exception ignored){missingImagePaths.add(path);}
        label.setIcon(new PawIcon(26, BURGUNDY));
        return label;
    }

    private JTable table(DefaultTableModel model){
        JTable table=new JTable(model);table.setRowHeight(32);table.setShowVerticalLines(false);
        table.setGridColor(BORDER);table.setSelectionBackground(LIGHT_PINK);
        table.setSelectionForeground(DARK_TEXT);table.setFont(uiFont(400,14));
        JTableHeader header=table.getTableHeader();header.setBackground(BEIGE);header.setForeground(DARK_TEXT);
        header.setFont(uiFont(700,14));header.setPreferredSize(new Dimension(header.getWidth(),36));
        return table;
    }

    private JButton primaryButton(String text,Runnable action){
        return styledButton(text, BURGUNDY, Color.WHITE, SECONDARY_BURGUNDY, action, 14);
    }

    private JButton secondaryButton(String text,Runnable action){
        return styledButton(text, LIGHT_BURGUNDY, BURGUNDY, ROSE_GOLD, action, 14);
    }

    private JButton smallButton(String text,Runnable action){
        if ("Delete".equalsIgnoreCase(text)) {
            return styledButton(text, new Color(249, 232, 235), DANGER,
                    new Color(241, 208, 214), action, 13);
        }
        return styledButton(text, LIGHT_BURGUNDY, BURGUNDY, ROSE_GOLD, action, 13);
    }

    private JButton successButton(String text, Runnable action) {
        return styledButton(text, SUCCESS, Color.WHITE, new Color(33, 103, 68), action, 14);
    }

    private JButton styledButton(String text, Color background, Color foreground,
                                 Color hover, Runnable action, int fontSize) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fill = getModel().isPressed() ? background.darker()
                        : getModel().isRollover() ? hover : background;
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.setColor(BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.dispose();
                super.paintComponent(graphics);
            }
        };
        button.setBackground(background);
        button.setForeground(foreground);
        button.setFont(uiFont(600, fontSize));
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(8, 12, 8, 12));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setRolloverEnabled(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(e -> action.run());
        return button;
    }

    private JPanel emptyState(String text){
        JPanel p=new JPanel(new GridBagLayout());p.setOpaque(false);p.add(new JLabel(text));return p;
    }

    private void addHoverEffect(JPanel panel) {
        panel.addMouseListener(new MouseAdapter() {
            private final Color original = panel.getBackground();
            @Override public void mouseEntered(MouseEvent e) {
                panel.setBackground(new Color(
                        Math.min(255, original.getRed() + 5),
                        Math.min(255, original.getGreen() + 3),
                        Math.min(255, original.getBlue() + 3)));
            }
            @Override public void mouseExited(MouseEvent e) {
                panel.setBackground(original);
            }
        });
    }

    private static class RoundedPanel extends JPanel {
        private final int radius;
        RoundedPanel(int radius, Color background) {
            this.radius = radius;
            setOpaque(false);
            setBackground(background);
        }
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.setColor(BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.dispose();
        }
    }

    private void showSuccess(String message){JOptionPane.showMessageDialog(this,message,"Success",JOptionPane.INFORMATION_MESSAGE);}
    private void showError(String message){JOptionPane.showMessageDialog(this,message,"Error",JOptionPane.ERROR_MESSAGE);}
    }
