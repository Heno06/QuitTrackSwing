import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;


import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;

public class QuitTrackSwing extends JFrame {
    private static final String APP_DIR = System.getProperty("user.home") + File.separator + ".quittrack";
    private static final String LOG_CSV = APP_DIR + File.separator + "logs.csv"; // date,cigs
    private static final String SETTINGS_PROP = APP_DIR + File.separator + "settings.properties";
    private static final String BACKUP_DIR = APP_DIR + File.separator + "backups";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ---------- Data Models ----------
    private final TreeMap<LocalDate, Integer> logs = new TreeMap<>();
    private final Settings settings = new Settings();

    // ---------- UI Components ----------
    private JLabel lblStreak;
    private JLabel lblSaved;
    private JLabel lblStatus;

    // Calendar tab
    private JLabel lblCalendarDate;
    private JLabel lblCalendarCigs;
    private LocalDate currentViewDate = LocalDate.now();

    // Weekly table
    private JTable tblWeekly;

    // Charts tab
    private JComboBox<String> cbChartMode;
    private ChartPanel cigsChartPanel;
    private ChartPanel savingsChartPanel;

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
        setSize(1040, 680);
        setLocationRelativeTo(null);
        setJMenuBar(buildMenuBar());

        JPanel root = new JPanel(new BorderLayout());
        root.add(buildHeader(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Home", buildHomePanel());
        tabs.addTab("Calendar", buildCalendarPanel());
        tabs.addTab("Weekly", buildWeeklyPanel());
        tabs.addTab("Charts", buildChartsPanel());
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
        JMenuItem clearAll = new JMenuItem("Clear All Logs");
        clearAll.addActionListener(e -> clearAllLogs());
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> dispose());
        file.add(export);
        file.add(clearAll);
        file.addSeparator();
        file.add(exit);

        JMenu help = new JMenu("Help");
        JMenuItem about = new JMenuItem("About QuitTrack");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "QuitTrack — simple cigarette tracking\nTracks streaks, savings, daily/weekly logs and charts.",
                "About", JOptionPane.INFORMATION_MESSAGE));
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
        JLabel subtitle = new JLabel("   Track smoke-free streaks, savings and trends");
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

        // Today input
        JPanel todayCard = card("Today");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        row.add(new JLabel("Date:"));
        JLabel todayLabel = new JLabel(LocalDate.now().format(DATE_FMT));
        todayLabel.setFont(todayLabel.getFont().deriveFont(Font.BOLD));
        row.add(todayLabel);
        row.add(new JLabel("Cigarettes:"));
        JSpinner spinnerToday = new JSpinner(new SpinnerNumberModel(getValue(LocalDate.now()), 0, 200, 1));
        ((JSpinner.DefaultEditor)spinnerToday.getEditor()).getTextField().setColumns(4);
        row.add(spinnerToday);
        JButton btnSave = primaryButton("Save Today");
        btnSave.addActionListener(e -> saveTodayAction(spinnerToday));
        row.add(btnSave);
        todayCard.add(row);

        // Stats
        JPanel statsCard = card("Your progress");
        lblStreak = bigLabel("Streak: —");
        lblSaved = mediumLabel("Saved: —");
        statsCard.add(lblStreak);
        statsCard.add(Box.createVerticalStrut(6));
        statsCard.add(lblSaved);

        gc.gridx = 0; gc.gridy = 0; gc.weightx = 0.6; p.add(todayCard, gc);
        gc.gridx = 1; gc.gridy = 0; gc.weightx = 0.4; p.add(statsCard, gc);

        return p;
    }


    // ----------------- Calendar Panel (Monthly Grid with design + Clear All) -----------------
    private YearMonth currentMonth = YearMonth.now();

    private JPanel buildCalendarPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(12, 12, 12, 12));

        // Top bar with navigation
        JPanel top = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 4));
        JButton prev = new JButton("◀ Prev");
        JButton next = new JButton("Next ▶");
        JLabel lblMonth = new JLabel(currentMonth.getMonth().toString() + " " + currentMonth.getYear());
        lblMonth.setFont(lblMonth.getFont().deriveFont(Font.BOLD, 18f));
        top.add(prev);
        top.add(lblMonth);
        top.add(next);
        p.add(top, BorderLayout.NORTH);

        // Calendar grid with spacing
        JPanel grid = new JPanel(new GridLayout(0, 7, 4, 4));
        grid.setBackground(Color.DARK_GRAY); // gaps show as dark lines
        p.add(grid, BorderLayout.CENTER);

        // Bottom bar with Clear All button
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton clearAllBtn = new JButton("Clear All Logs");
        clearAllBtn.addActionListener(e -> clearAllLogs());
        bottom.add(clearAllBtn);
        p.add(bottom, BorderLayout.SOUTH);

        // Refresh function builds the calendar
        Runnable refresh = () -> {
            grid.removeAll();
            lblMonth.setText(currentMonth.getMonth().toString() + " " + currentMonth.getYear());

            // Day of week headers
            String[] days = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
            for (String d : days) {
                JLabel header = new JLabel(d, SwingConstants.CENTER);
                header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
                header.setOpaque(true);
                header.setBackground(new Color(230, 230, 230));
                header.setBorder(new LineBorder(Color.BLACK));
                grid.add(header);
            }

            LocalDate firstDay = currentMonth.atDay(1);
            int startDow = firstDay.getDayOfWeek().getValue() % 7; // Sunday=0
            int daysInMonth = currentMonth.lengthOfMonth();

            // Blank cells before first day
            for (int i = 0; i < startDow; i++) {
                JPanel blank = new JPanel();
                blank.setBackground(Color.WHITE);
                blank.setBorder(new LineBorder(Color.BLACK));
                grid.add(blank);
            }

            // Day cells
            for (int day = 1; day <= daysInMonth; day++) {
                LocalDate date = currentMonth.atDay(day);

                JPanel cell = new JPanel();
                cell.setBackground(Color.WHITE);
                cell.setBorder(new LineBorder(Color.BLACK, 1));
                cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
                cell.setPreferredSize(new Dimension(100, 70));

                // Highlight today
                if (date.equals(LocalDate.now())) {
                    cell.setBackground(new Color(200, 230, 255));
                }

                JLabel lblDay = new JLabel(String.valueOf(day));
                lblDay.setFont(lblDay.getFont().deriveFont(Font.BOLD, 14f));
                lblDay.setAlignmentX(Component.LEFT_ALIGNMENT);
                cell.add(lblDay);

                int cigs = getValue(date);
                double pricePerCig = settings.cigsPerPack > 0 ? settings.pricePerPack / settings.cigsPerPack : 0.0;
                double saved = Math.max(0, settings.baselinePerDay - cigs) * pricePerCig;

                JLabel lblData = new JLabel("Cigs: " + cigs);
                lblData.setFont(lblData.getFont().deriveFont(11f));
                lblData.setAlignmentX(Component.LEFT_ALIGNMENT);
                cell.add(lblData);

                JLabel lblSaved = new JLabel("Saved: " + settings.currency + String.format("%.2f", saved));
                lblSaved.setFont(lblSaved.getFont().deriveFont(11f));
                lblSaved.setAlignmentX(Component.LEFT_ALIGNMENT);
                cell.add(lblSaved);

                grid.add(cell);
            }

            // Fill trailing blanks
            int totalCells = 7 * 6; // 6 rows
            int used = startDow + daysInMonth + 7; // +7 headers
            for (int i = used; i < totalCells + 7; i++) {
                JPanel blank = new JPanel();
                blank.setBackground(Color.WHITE);
                blank.setBorder(new LineBorder(Color.BLACK));
                grid.add(blank);
            }

            grid.revalidate();
            grid.repaint();
        };

        prev.addActionListener(e -> { currentMonth = currentMonth.minusMonths(1); refresh.run(); });
        next.addActionListener(e -> { currentMonth = currentMonth.plusMonths(1); refresh.run(); });

        refresh.run();
        return p;
    }




    private void refreshCalendarLabels() {
        lblCalendarDate.setText(currentViewDate.format(DATE_FMT));
        lblCalendarCigs.setText("Cigarettes smoked: " + getValue(currentViewDate));
    }

    private void clearAllLogs() {
        int ok = JOptionPane.showConfirmDialog(this,
                "This will permanently delete ALL logs, including today's entry. Continue?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            logs.clear();
            saveLogs();
            refreshWeeklyTable();
            refreshComputedLabels();
            refreshCharts();
            updateStatus("All logs cleared");
            JOptionPane.showMessageDialog(this, "All logs deleted.");
        }
    }

    // ----------------- Weekly Panel -----------------
    // ======= Weekly Table (Modern look) =======
    private JPanel buildWeeklyPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(new EmptyBorder(16,16,16,16));

        // Crear tabla
        tblWeekly = new JTable();
        tblWeekly.setFillsViewportHeight(true);
        tblWeekly.setRowHeight(28);
        tblWeekly.setShowGrid(true);
        tblWeekly.setGridColor(new Color(220, 226, 235));
        tblWeekly.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        // Cabecera de la tabla con estilo
        JTableHeader header = tblWeekly.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 13));
        header.setBackground(new Color(240, 242, 250));
        header.setForeground(new Color(50, 50, 70));
        header.setBorder(new MatteBorder(0, 0, 1, 0, new Color(200, 200, 200)));

        // Zebra rows (filas alternadas con color)
        tblWeekly.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(245, 248, 252));
                } else {
                    c.setBackground(new Color(200, 220, 255)); // color cuando seleccionas fila
                }
                return c;
            }
        });

        // Scroll con borde bonito
        JScrollPane scroll = new JScrollPane(tblWeekly);
        scroll.setBorder(new CompoundBorder(
                new LineBorder(new Color(210, 215, 225), 1, true),
                new EmptyBorder(6,6,6,6)
        ));

        // Refrescar datos
        refreshWeeklyTable();

        // Card envolviendo la tabla
        JPanel card = card("Weekly Summary");
        card.setLayout(new BorderLayout());
        card.add(scroll, BorderLayout.CENTER);

        p.add(card, BorderLayout.CENTER);
        return p;
    }


    // ----------------- Charts Panel -----------------
    private JPanel buildChartsPanel() {
        JPanel p = new JPanel(new BorderLayout(12,12));
        p.setBorder(new EmptyBorder(12,12,12,12));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        cbChartMode = new JComboBox<>(new String[]{"Weekly", "Monthly"});
        cbChartMode.addActionListener(e -> refreshCharts());
        top.add(new JLabel("View:"));
        top.add(cbChartMode);
        p.add(top, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(1,2,16,0));
        cigsChartPanel = new ChartPanel(createCigsChart("Weekly"));
        savingsChartPanel = new ChartPanel(createSavingsChart("Weekly"));
        grid.add(cigsChartPanel);
        grid.add(savingsChartPanel);
        p.add(grid, BorderLayout.CENTER);

        return p;
    }

    private void refreshCharts() {
        String mode = Objects.toString(cbChartMode.getSelectedItem(), "Weekly");
        cigsChartPanel.setChart(createCigsChart(mode));
        savingsChartPanel.setChart(createSavingsChart(mode));
    }

    private JFreeChart createCigsChart(String mode) {
        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        if ("Monthly".equals(mode)) {
            LocalDate start = LocalDate.now().minusDays(29);
            for (int i = 0; i < 30; i++) {
                LocalDate d = start.plusDays(i);
                ds.addValue(getValue(d), "Cigarettes", d.format(DateTimeFormatter.ofPattern("MM-dd")));
            }
            return ChartFactory.createLineChart("Cigarettes per Day (30 days)", "Day", "Cigarettes", ds);
        } else {
            LocalDate end = LocalDate.now();
            LocalDate cursor = end.minusWeeks(7).with(DayOfWeek.MONDAY);
            while (!cursor.isAfter(end)) {
                LocalDate weekEnd = cursor.with(DayOfWeek.SUNDAY);
                int total = 0;
                for (LocalDate d = cursor; !d.isAfter(weekEnd); d = d.plusDays(1)) {
                    total += getValue(d);
                }
                ds.addValue(total, "Cigarettes", cursor.format(DateTimeFormatter.ofPattern("MM-dd")));
                cursor = cursor.plusWeeks(1);
            }
            return ChartFactory.createLineChart("Cigarettes per Week (8 weeks)", "Week start", "Cigarettes", ds);
        }
    }

    private JFreeChart createSavingsChart(String mode) {
        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        double pricePerCig = settings.cigsPerPack > 0 ? settings.pricePerPack / settings.cigsPerPack : 0.0;
        if ("Monthly".equals(mode)) {
            LocalDate start = LocalDate.now().minusDays(29);
            for (int i = 0; i < 30; i++) {
                LocalDate d = start.plusDays(i);
                int diff = Math.max(0, settings.baselinePerDay - getValue(d));
                ds.addValue(diff * pricePerCig, "Saved (" + settings.currency + ")", d.format(DateTimeFormatter.ofPattern("MM-dd")));
            }
            return ChartFactory.createLineChart("Money Saved per Day (30 days)", "Day", settings.currency, ds);
        } else {
            LocalDate end = LocalDate.now();
            LocalDate cursor = end.minusWeeks(7).with(DayOfWeek.MONDAY);
            while (!cursor.isAfter(end)) {
                LocalDate weekEnd = cursor.with(DayOfWeek.SUNDAY);
                double total = 0;
                for (LocalDate d = cursor; !d.isAfter(weekEnd); d = d.plusDays(1)) {
                    int diff = Math.max(0, settings.baselinePerDay - getValue(d));
                    total += diff * pricePerCig;
                }
                ds.addValue(total, "Saved (" + settings.currency + ")", cursor.format(DateTimeFormatter.ofPattern("MM-dd")));
                cursor = cursor.plusWeeks(1);
            }
            return ChartFactory.createLineChart("Money Saved per Week (8 weeks)", "Week start", settings.currency, ds);
        }
    }

    // ----------------- Settings Panel -----------------
    private JPanel buildSettingsPanel() {
        JPanel p = padded(new JPanel());
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JTextField tfQuitDate = new JTextField(settings.quitDate.toString(), 12);
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
                refreshCalendarLabels();
                refreshCharts();
                JOptionPane.showMessageDialog(this, "Settings saved.");
            } catch (Exception ex) {
                showError(ex.getMessage());
            }
        });
        p.add(Box.createVerticalStrut(8));
        p.add(btnSave);

        return p;
    }

    // ---------- Persistence ----------
    private void loadLogs() {
        logs.clear();
        Path p = Paths.get(LOG_CSV);
        if (!Files.exists(p)) return;
        try (BufferedReader br = Files.newBufferedReader(p)) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 2) {
                    LocalDate d = LocalDate.parse(parts[0].trim());
                    int cigs = Integer.parseInt(parts[1].trim());
                    logs.put(d, cigs);
                }
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
        props.setProperty("quitDate", settings.quitDate.toString());
        try (OutputStream out = Files.newOutputStream(Paths.get(SETTINGS_PROP))) {
            props.store(out, "QuitTrack settings");
        } catch (IOException e) {
            showError("Failed to save settings: " + e.getMessage());
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
        } catch (IOException e) {
            showError("Backup failed: " + e.getMessage());
        }
    }

    private void ensureAppFolders() {
        try {
            Files.createDirectories(Paths.get(APP_DIR));
            Files.createDirectories(Paths.get(BACKUP_DIR));
        } catch (IOException e) {
            showError("Cannot create app folders: " + e.getMessage());
        }
    }

    // ---------- Computations ----------
    private void refreshComputedLabels() {
        int streak = computeSmokeFreeStreak();
        lblStreak.setText("Streak: " + streak + (streak == 1 ? " day" : " days"));

        double saved = computeMoneySavedTotal();
        lblSaved.setText("Saved: " + settings.currency + String.format(Locale.US, "%.2f", saved));
    }

    private int computeSmokeFreeStreak() {
        int streak = 0;
        LocalDate d = LocalDate.now();
        while (true) {
            int c = getValue(d);
            if (c == 0) {
                streak++;
                d = d.minusDays(1);
            } else break;
            if (settings.quitDate != null && d.isBefore(settings.quitDate)) break;
        }
        return streak;
    }

    private double computeMoneySavedTotal() {
        if (settings.cigsPerPack <= 0) return 0;
        double pricePerCig = settings.pricePerPack / settings.cigsPerPack;
        double sum = 0;
        for (Map.Entry<LocalDate, Integer> e : logs.entrySet()) {
            int diff = Math.max(0, settings.baselinePerDay - e.getValue());
            sum += diff * pricePerCig;
        }
        return sum;
    }

    private void refreshWeeklyTable() {
        if (tblWeekly == null) return;
        String[] cols = {"Week (Mon–Sun)", "Total cigarettes", "Average/day"};
        DefaultTableModel m = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int ci) { return ci==0?String.class:(ci==1?Integer.class:Double.class); }
        };
        LocalDate end = LocalDate.now();
        LocalDate cursor = end.minusWeeks(7).with(DayOfWeek.MONDAY);
        while (!cursor.isAfter(end)) {
            LocalDate weekEnd = cursor.with(DayOfWeek.SUNDAY);
            int total = 0;
            int days = 0;
            for (LocalDate d = cursor; !d.isAfter(weekEnd); d = d.plusDays(1)) {
                total += getValue(d);
                days++;
            }
            double avg = days==0?0:(double)total/days;
            m.addRow(new Object[]{cursor.format(DATE_FMT)+" — "+weekEnd.format(DATE_FMT), total, Math.round(avg*100.0)/100.0});
            cursor = cursor.plusWeeks(1);
        }
        tblWeekly.setModel(m);
        formatColumns(tblWeekly);
    }

    private void saveTodayAction(JSpinner spinner) {
        try {
            int value = (Integer) spinner.getValue();
            if (value < 0 || value > 200) throw new IllegalArgumentException("Today's cigarettes must be 0–200.");
            logs.put(LocalDate.now(), value);
            saveLogs();
            refreshWeeklyTable();
            refreshComputedLabels();
            refreshCharts();
            updateStatus("Saved today");
            JOptionPane.showMessageDialog(this, "Saved");
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }
    // ---------- Motivation ----------
    private void showMotivationIfEnabled() {
        if (!settings.notificationsEnabled) return;
        String[] quotes = {
                "Small steps every day beat big plans once a year.",
                "Your lungs are already thanking you.",
                "One day at a time. One choice at a time.",
                "Cravings pass. Pride lasts.",
                "Today’s zero is tomorrow’s streak."
        };
        JOptionPane.showMessageDialog(this, quotes[new Random().nextInt(quotes.length)],
                "Keep going!", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---------- UI Helpers ----------
    private JPanel padded(JPanel p) {
        return padded(p, 16);
    }
    private JPanel padded(JPanel p, int pad) {
        p.setBorder(new EmptyBorder(pad,pad,pad,pad));
        return p;
    }
    private JPanel card(String title) {
        JPanel c = new JPanel(); c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.setBorder(new CompoundBorder(new TitledBorder(new LineBorder(new Color(210,210,210)), title), new EmptyBorder(10,10,10,10)));
        c.setBackground(new Color(250,250,252));
        return c;
    }
    private JLabel bigLabel(String s) { JLabel l=new JLabel(s); l.setFont(l.getFont().deriveFont(Font.BOLD,22f)); return l; }
    private JLabel mediumLabel(String s) { JLabel l=new JLabel(s); l.setFont(l.getFont().deriveFont(Font.PLAIN,16f)); return l; }
    private JButton primaryButton(String text) { JButton b=new JButton(text); b.setBorder(new CompoundBorder(new LineBorder(new Color(60,120,220)), new EmptyBorder(6,12,6,12))); return b; }
    private JPanel labeled(String label, JComponent field) {
        JPanel row=new JPanel(new FlowLayout(FlowLayout.LEFT,12,6));
        JLabel l=new JLabel(label+": "); l.setPreferredSize(new Dimension(190,24));
        row.add(l); row.add(field); return row;
    }

    private JComponent buildStatusBar() { JPanel bar=new JPanel(new BorderLayout()); lblStatus=new JLabel("  "); bar.add(lblStatus, BorderLayout.WEST); return bar; }
    private void updateStatus(String msg) { lblStatus.setText("  "+LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))+" — "+msg); }

    private void styleTable(JTable t) { t.setFillsViewportHeight(true); t.setRowHeight(24); t.setAutoCreateRowSorter(true); }
    private void formatColumns(JTable t) {
        DefaultTableCellRenderer right=new DefaultTableCellRenderer(); right.setHorizontalAlignment(SwingConstants.RIGHT);
        for(int i=0;i<t.getColumnCount();i++) if(Number.class.isAssignableFrom(t.getColumnClass(i))) t.getColumnModel().getColumn(i).setCellRenderer(right);
    }

    private static void setNiceLAF() {
        try { for (UIManager.LookAndFeelInfo info:UIManager.getInstalledLookAndFeels()) if("Nimbus".equals(info.getName())) { UIManager.setLookAndFeel(info.getClassName()); return; } } catch(Exception ignored){}
    }
    private static void showError(String msg){ JOptionPane.showMessageDialog(null,msg,"Error",JOptionPane.ERROR_MESSAGE); }

    private static double parsePositiveDouble(String s,String f){ double v=Double.parseDouble(s); if(v<=0) throw new IllegalArgumentException(f+" must be >0."); return v; }
    private static int parsePositiveInt(String s,String f){ int v=Integer.parseInt(s); if(v<=0) throw new IllegalArgumentException(f+" must be >0."); return v; }
    private static int parseNonNegativeInt(String s,String f){ int v=Integer.parseInt(s); if(v<0) throw new IllegalArgumentException(f+" must be ≥0."); return v; }
    private static double parseDoubleOrDefault(String s,double d){ try{return s==null?d:Double.parseDouble(s);}catch(Exception e){return d;} }
    private static int parseIntOrDefault(String s,int d){ try{return s==null?d:Integer.parseInt(s);}catch(Exception e){return d;} }

    private int getValue(LocalDate d) { return logs.getOrDefault(d, 0); }

    private static class Settings {
        String currency = "$";
        double pricePerPack = 7.0;
        int cigsPerPack = 20;
        int baselinePerDay = 20;
        boolean notificationsEnabled = true;
        LocalDate quitDate = LocalDate.now();
    }
}
