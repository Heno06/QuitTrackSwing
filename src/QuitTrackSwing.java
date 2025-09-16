import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class QuitTrackSwing extends JFrame {
    // ---------- Constants & Paths ----------
    private static final String APP_DIR = System.getProperty("user.home") + File.separator + ".quittrack";
    private static final String LOG_CSV = APP_DIR + File.separator + "logs.csv"; // date,cigs
    private static final String SETTINGS_PROP = APP_DIR + File.separator + "settings.properties";
    private static final String BACKUP_DIR = APP_DIR + File.separator + "backups";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ---------- Data Models ----------
    private final TreeMap<LocalDate, Integer> logs = new TreeMap<>(); // keeps dates sorted
    private final Settings settings = new Settings();

    // ---------- UI Components ----------
    private JLabel lblStreak;
    private JLabel lblSaved;
    private JLabel lblStatus;
    private JSpinner spinnerToday;
    private JTable tblDaily;
    private JTable tblWeekly;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            setNiceLAF();
            new QuitTrackSwing().setVisible(true);
        });
    }

    public QuitTrackSwing() {
        super("QuitTrack — Swing");
        ensureAppFolders();
        loadSettings();
        loadLogs();
        buildUI();
        showMotivationIfEnabled();
        refreshComputedLabels();
        updateStatus("Ready");
    }

    // ---------- UI Construction ----------
    private void buildUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(960, 620);
        setLocationRelativeTo(null);
        setJMenuBar(buildMenuBar());

        JPanel root = new JPanel(new BorderLayout());
        root.add(buildHeader(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Home", buildHomePanel());
        tabs.addTab("Daily", buildDailyPanel());
        tabs.addTab("Weekly", buildWeeklyPanel());
        tabs.addTab("Settings", buildSettingsPanel());
        root.add(tabs, BorderLayout.CENTER);

        root.add(buildStatusBar(), BorderLayout.SOUTH);
        setContentPane(root);
    }

    private JMenuBar buildMenuBar() {
        JMenuBar mb = new JMenuBar();
        JMenu file = new JMenu("File");
        JMenuItem export = new JMenuItem("Export CSV Backup");
        export.addActionListener(e -> exportBackup());
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> dispose());
        file.add(export);
        file.addSeparator();
        file.add(exit);

        JMenu help = new JMenu("Help");
        JMenuItem about = new JMenuItem("About QuitTrack");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this, JOptionPane.INFORMATION_MESSAGE));
        help.add(about);

        mb.add(file);
        mb.add(help);
        return mb;
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new MatteBorder(0, 0, 1, 0, new Color(220,220,220)));
        JLabel title = new JLabel("   🚭 QuitTrack");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JLabel subtitle = new JLabel("   Track smoke‑free streaks and savings");
        subtitle.setForeground(new Color(90, 90, 90));
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);
        left.add(title);
        left.add(Box.createVerticalStrut(2));
        left.add(subtitle);
        header.add(left, BorderLayout.WEST);
        return header;
    }

    private JPanel buildHomePanel() {
        JPanel p = padded(new JPanel(new GridBagLayout()), 16);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(8,8,8,8);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;

        // Card 1: Today input
        JPanel todayCard = card("Today");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        row.add(new JLabel("Date:"));
        JLabel todayLabel = new JLabel(LocalDate.now().format(DATE_FMT));
        todayLabel.setFont(todayLabel.getFont().deriveFont(Font.BOLD));
        row.add(todayLabel);
        row.add(new JLabel("Cigarettes:"));
        spinnerToday = new JSpinner(new SpinnerNumberModel(getTodayValue(), 0, 200, 1));
        ((JSpinner.DefaultEditor)spinnerToday.getEditor()).getTextField().setColumns(4);
        row.add(spinnerToday);
        JButton btnSave = primaryButton("Save Today");
        btnSave.addActionListener(this::saveTodayAction);
        row.add(btnSave);
        todayCard.add(row);

        // Card 2: Stats
        JPanel statsCard = card("Your progress");
        lblStreak = bigLabel("Streak: —");
        lblSaved = mediumLabel("Saved: —");
        statsCard.add(lblStreak);
        statsCard.add(Box.createVerticalStrut(6));
        statsCard.add(lblSaved);

        // Layout grid
        gc.gridx = 0; gc.gridy = 0; gc.weightx = 0.6; p.add(todayCard, gc);
        gc.gridx = 1; gc.gridy = 0; gc.weightx = 0.4; p.add(statsCard, gc);

        // Card 3: Tip
        JPanel tip = card("Tip");
        JTextArea help = new JTextArea("Set your baseline/day & price per pack in Settings." + "Savings = max(0, baseline − actual) × price-per-cigarette.");
        help.setEditable(false);
        help.setLineWrap(true);
        help.setWrapStyleWord(true);
        help.setBackground(tip.getBackground());
        tip.add(help);
        gc.gridx = 0; gc.gridy = 1; gc.gridwidth = 2; gc.weightx = 1; p.add(tip, gc);

        return p;
    }

    private JPanel buildDailyPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(8, 8, 8, 8));

        tblDaily = new JTable();
        styleTable(tblDaily);
        refreshDailyTable();
        p.add(new JScrollPane(tblDaily), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnExport = new JButton("Export CSV Backup");
        btnExport.addActionListener(e -> exportBackup());
        JButton btnClearToday = new JButton("Clear Today");
        btnClearToday.addActionListener(e -> {
            logs.remove(LocalDate.now());
            saveLogs();
            refreshDailyTable();
            refreshWeeklyTable();
            refreshComputedLabels();
            updateStatus("Cleared today");
        });
        actions.add(btnExport);
        actions.add(btnClearToday);
        p.add(actions, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildWeeklyPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(8, 8, 8, 8));

        tblWeekly = new JTable();
        styleTable(tblWeekly);
        refreshWeeklyTable();
        p.add(new JScrollPane(tblWeekly), BorderLayout.CENTER);
        return p;
    }

    private JPanel buildSettingsPanel() {
        JPanel p = padded(new JPanel());
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JTextField tfQuitDate = new JTextField(settings.quitDate != null ? settings.quitDate.toString() : LocalDate.now().toString(), 12);
        JTextField tfPricePerPack = new JTextField(String.valueOf(settings.pricePerPack), 10);
        JTextField tfCigsPerPack = new JTextField(String.valueOf(settings.cigsPerPack), 10);
        JTextField tfBaseline = new JTextField(String.valueOf(settings.baselinePerDay), 10);

        JComboBox<String> cbCurrency = new JComboBox<>(new String[]{"$","€","£","AMD","RON"});
        cbCurrency.setSelectedItem(settings.currency);

        JCheckBox cbNotify = new JCheckBox("Show motivational quote at startup", settings.notificationsEnabled);

        p.add(labeled("Quit date (yyyy-MM-dd)", tfQuitDate));
        p.add(labeled("Price per pack", tfPricePerPack));
        p.add(labeled("Cigarettes per pack", tfCigsPerPack));
        p.add(labeled("Baseline cigarettes/day", tfBaseline));
        p.add(labeled("Currency", cbCurrency));
        p.add(cbNotify);

        JButton btnSave = primaryButton("Save Settings");
        btnSave.addActionListener(e -> {
            try {
                settings.quitDate = LocalDate.parse(tfQuitDate.getText().trim());
                settings.pricePerPack = parsePositiveDouble(tfPricePerPack.getText().trim(), "Price per pack");
                settings.cigsPerPack = parsePositiveInt(tfCigsPerPack.getText().trim(), "Cigarettes per pack");
                settings.baselinePerDay = parseNonNegativeInt(tfBaseline.getText().trim(), "Baseline per day");
                settings.currency = (String) cbCurrency.getSelectedItem();
                settings.notificationsEnabled = cbNotify.isSelected();
                saveSettings();
                refreshComputedLabels();
                updateStatus("Settings saved");
                JOptionPane.showMessageDialog(this, "Settings saved.");
            } catch (Exception ex) {
                showError(ex.getMessage());
            }
        });
        p.add(Box.createVerticalStrut(8));
        p.add(btnSave);

        return p;
    }

    private JPanel labeled(String label, JComponent field) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        JLabel l = new JLabel(label + ": ");
        l.setPreferredSize(new Dimension(190, 24));
        row.add(l);
        row.add(field);
        return row;
    }

    private JPanel card(String title) {
        JPanel c = new JPanel();
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.setBorder(new CompoundBorder(new TitledBorder(new LineBorder(new Color(210,210,210)), title), new EmptyBorder(10, 10, 10, 10)));
        c.setOpaque(true);
        c.setBackground(new Color(250, 250, 252));
        return c;
    }

    private JPanel padded(JPanel p) { return padded(p, 16); }
    private JPanel padded(JPanel p, int pad) {
        p.setBorder(new EmptyBorder(pad, pad, pad, pad));
        return p;
    }

    private JLabel bigLabel(String s) {
        JLabel l = new JLabel(s);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 22f));
        return l;
    }
    private JLabel mediumLabel(String s) {
        JLabel l = new JLabel(s);
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 16f));
        l.setForeground(new Color(40, 40, 40));
        return l;
    }
    private JButton primaryButton(String text) {
        JButton b = new JButton(text);
        b.setBorder(new CompoundBorder(new LineBorder(new Color(60, 120, 220)), new EmptyBorder(6, 12, 6, 12)));
        return b;
    }

    private JComponent buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(new MatteBorder(1, 0, 0, 0, new Color(220,220,220)));
        lblStatus = new JLabel("  ");
        bar.add(lblStatus, BorderLayout.WEST);
        return bar;
    }

    private void updateStatus(String message) {
        lblStatus.setText("  " + DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalTime.now()) + " — " + message);
    }

    // ---------- Actions ----------
    private void saveTodayAction(ActionEvent e) {
        try {
            int value = (Integer) spinnerToday.getValue();
            if (value < 0 || value > 200) throw new IllegalArgumentException("Today's cigarettes must be 0–200.");
            logs.put(LocalDate.now(), value);
            saveLogs();
            refreshDailyTable();
            refreshWeeklyTable();
            refreshComputedLabels();
            updateStatus("Saved today");
            JOptionPane.showMessageDialog(this, "Saved");
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    // ---------- Computations ----------
    private void refreshComputedLabels() {
        int streak = computeSmokeFreeStreak();
        lblStreak.setText("Streak: " + streak + (streak == 1 ? " day" : " days"));

        double saved = computeMoneySaved();
        lblSaved.setText("Saved: " + settings.currency + String.format(Locale.US, "%.2f", saved));
    }

    private int computeSmokeFreeStreak() {
        // Count consecutive days from today backwards with 0 cigarettes
        int streak = 0;
        LocalDate d = LocalDate.now();
        while (true) {
            Integer c = logs.getOrDefault(d, 0);
            if (c == 0) {
                streak++;
                d = d.minusDays(1);
            } else {
                break;
            }
            if (settings.quitDate != null && d.isBefore(settings.quitDate)) break;
        }
        return streak;
    }

    private double computeMoneySaved() {
        // Savings per day: max(0, baseline - actual) * pricePerCigarette
        if (settings.cigsPerPack <= 0) return 0;
        double pricePerCig = settings.pricePerPack / settings.cigsPerPack;
        double sum = 0;
        for (Map.Entry<LocalDate, Integer> e : logs.entrySet()) {
            if (settings.quitDate != null && e.getKey().isBefore(settings.quitDate)) continue;
            int actual = e.getValue();
            int diff = Math.max(0, settings.baselinePerDay - actual);
            sum += diff * pricePerCig;
        }
        return sum;
    }

    private void refreshDailyTable() {
        String[] cols = {"Date", "Cigarettes"};
        DefaultTableModel m = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
            @Override public Class<?> getColumnClass(int columnIndex) { return columnIndex==1 ? Integer.class : String.class; }
        };
        LocalDate start = LocalDate.now().minusDays(29);
        for (int i = 0; i < 30; i++) {
            LocalDate d = start.plusDays(i);
            m.addRow(new Object[]{d.format(DATE_FMT), logs.getOrDefault(d, 0)});
        }
        tblDaily.setModel(m);
        formatColumns(tblDaily);
    }

    private void refreshWeeklyTable() {
        String[] cols = {"Week (Mon–Sun)", "Total cigarettes", "Average/day"};
        DefaultTableModel m = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
            @Override public Class<?> getColumnClass(int columnIndex) { return columnIndex==0 ? String.class : (columnIndex==1 ? Integer.class : Double.class); }
        };
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusWeeks(7); // last ~8 weeks window
        LocalDate cursor = start.with(DayOfWeek.MONDAY);
        while (!cursor.isAfter(end)) {
            LocalDate weekEnd = cursor.with(DayOfWeek.SUNDAY);
            int total = 0;
            int days = 0;
            for (LocalDate d = cursor; !d.isAfter(weekEnd); d = d.plusDays(1)) {
                total += logs.getOrDefault(d, 0);
                days++;
            }
            double avg = days == 0 ? 0 : (double) total / days;
            String label = cursor.format(DATE_FMT) + " — " + weekEnd.format(DATE_FMT);
            m.addRow(new Object[]{label, total, round2(avg)});
            cursor = cursor.plusWeeks(1);
        }
        tblWeekly.setModel(m);
        formatColumns(tblWeekly);
    }

    private double round2(double v) { return Math.round(v*100.0)/100.0; }

    private int getTodayValue() {
        return logs.getOrDefault(LocalDate.now(), 0);
    }

    // ---------- Persistence ----------
    private void ensureAppFolders() {
        try {
            Files.createDirectories(Paths.get(APP_DIR));
            Files.createDirectories(Paths.get(BACKUP_DIR));
        } catch (IOException e) {
            showError("Cannot create app folders: " + e.getMessage());
        }
    }

    private void loadLogs() {
        logs.clear();
        Path p = Paths.get(LOG_CSV);
        if (!Files.exists(p)) return;
        try (BufferedReader br = Files.newBufferedReader(p)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split(",");
                if (parts.length != 2) continue;
                LocalDate d = LocalDate.parse(parts[0].trim());
                int cigs = Integer.parseInt(parts[1].trim());
                if (cigs >= 0 && cigs <= 200) logs.put(d, cigs);
            }
        } catch (Exception e) {
            showError("Failed to load logs: " + e.getMessage());
        }
    }

    private void saveLogs() {
        Path p = Paths.get(LOG_CSV);
        try (BufferedWriter bw = Files.newBufferedWriter(p)) {
            for (Map.Entry<LocalDate, Integer> e : logs.entrySet()) {
                bw.write(e.getKey().toString() + "," + e.getValue());
                bw.newLine();
            }
        } catch (IOException e) {
            showError("Failed to save logs: " + e.getMessage());
        }
    }

    private void exportBackup() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path src = Paths.get(LOG_CSV);
        Path dst = Paths.get(BACKUP_DIR + File.separator + "logs_" + ts + ".csv");
        try {
            if (!Files.exists(src)) {
                showError("No logs to export yet.");
                return;
            }
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            updateStatus("Backup saved");
            JOptionPane.showMessageDialog(this, "Backup saved: " + dst.toAbsolutePath());
        } catch (IOException e) {
            showError("Backup failed: " + e.getMessage());
        }
    }

    private void loadSettings() {
        Properties props = new Properties();
        Path p = Paths.get(SETTINGS_PROP);
        if (Files.exists(p)) {
            try (InputStream in = Files.newInputStream(p)) {
                props.load(in);
            } catch (IOException e) {
                showError("Failed to load settings: " + e.getMessage());
            }
        }
        settings.currency = props.getProperty("currency", "$");
        settings.pricePerPack = parseDoubleOrDefault(props.getProperty("pricePerPack"), 7.0);
        settings.cigsPerPack = parseIntOrDefault(props.getProperty("cigsPerPack"), 20);
        settings.baselinePerDay = parseIntOrDefault(props.getProperty("baselinePerDay"), 20);
        settings.notificationsEnabled = Boolean.parseBoolean(props.getProperty("notificationsEnabled", "true"));
        String qd = props.getProperty("quitDate");
        settings.quitDate = (qd == null || qd.isBlank()) ? LocalDate.now() : LocalDate.parse(qd);
    }

    private void saveSettings() {
        Properties props = new Properties();
        props.setProperty("currency", settings.currency);
        props.setProperty("pricePerPack", String.valueOf(settings.pricePerPack));
        props.setProperty("cigsPerPack", String.valueOf(settings.cigsPerPack));
        props.setProperty("baselinePerDay", String.valueOf(settings.baselinePerDay));
        props.setProperty("notificationsEnabled", String.valueOf(settings.notificationsEnabled));
        props.setProperty("quitDate", settings.quitDate != null ? settings.quitDate.toString() : "");
        try (OutputStream out = Files.newOutputStream(Paths.get(SETTINGS_PROP))) {
            props.store(out, "QuitTrack settings");
        } catch (IOException e) {
            showError("Failed to save settings: " + e.getMessage());
        }
    }

    // ---------- Motivation ----------
    private void showMotivationIfEnabled() {
        if (!settings.notificationsEnabled) return;
        String[] quotes = new String[]{
                "Small steps every day beat big plans once a year.",
                "Your lungs are already thanking you.",
                "One day at a time. One choice at a time.",
                "Cravings pass. Pride lasts.",
                "Today’s zero is tomorrow’s streak."
        };
        int idx = new Random().nextInt(quotes.length);
        JOptionPane.showMessageDialog(this, quotes[idx], "Keep going!", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---------- Table styling ----------
    private void styleTable(JTable t) {
        t.setFillsViewportHeight(true);
        t.setRowHeight(24);
        t.setAutoCreateRowSorter(true);
        JTableHeader h = t.getTableHeader();
        h.setReorderingAllowed(false);
        h.setFont(h.getFont().deriveFont(Font.BOLD));
    }

    private void formatColumns(JTable t) {
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        for (int i = 0; i < t.getColumnCount(); i++) {
            if (Number.class.isAssignableFrom(t.getColumnClass(i))) {
                t.getColumnModel().getColumn(i).setCellRenderer(right);
            }
        }
        t.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    }

    // ---------- Utils ----------
    private static void setNiceLAF() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    return;
                }
            }
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
    }

    private static void showError(String msg) {
        JOptionPane.showMessageDialog(null, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private static double parsePositiveDouble(String s, String field) {
        double v = Double.parseDouble(s);
        if (v <= 0) throw new IllegalArgumentException(field + " must be > 0.");
        return v;
    }

    private static int parsePositiveInt(String s, String field) {
        int v = Integer.parseInt(s);
        if (v <= 0) throw new IllegalArgumentException(field + " must be > 0.");
        return v;
    }

    private static int parseNonNegativeInt(String s, String field) {
        int v = Integer.parseInt(s);
        if (v < 0) throw new IllegalArgumentException(field + " must be ≥ 0.");
        return v;
    }

    private static double parseDoubleOrDefault(String s, double def) {
        try { return s == null ? def : Double.parseDouble(s); } catch (Exception e) { return def; }
    }

    private static int parseIntOrDefault(String s, int def) {
        try { return s == null ? def : Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    // ---------- Settings POJO ----------
    private static class Settings {
        String currency = "$";
        double pricePerPack = 7.0;
        int cigsPerPack = 20;
        int baselinePerDay = 20; // typical previous habit
        boolean notificationsEnabled = true;
        LocalDate quitDate = LocalDate.now();
    }
}
