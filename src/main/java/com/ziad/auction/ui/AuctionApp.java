package com.ziad.auction.ui;

import com.ziad.auction.model.Auction;
import com.ziad.auction.model.AuctionException;
import com.ziad.auction.model.AuctionHouse;
import com.ziad.auction.model.Money;
import com.ziad.auction.model.User;
import com.ziad.auction.service.Storage;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;

/** Swing desktop client: {@code java -cp out com.ziad.auction.ui.AuctionApp [data-file]}. */
public final class AuctionApp extends JFrame {
    private static final long serialVersionUID = 1L;

    private final transient AuctionHouse house;
    private final transient Storage storage;
    private final transient User user;
    private final AuctionTableModel model = new AuctionTableModel();
    private final JTable table = new JTable(model);
    private final JTextField search = new JTextField(18);
    private final JComboBox<String> filter = new JComboBox<>(new String[] {"Open auctions", "All auctions"});
    private final JLabel balance = new JLabel();

    AuctionApp(AuctionHouse house, Storage storage, User user) {
        super("Online Auction - " + user.username());
        this.house = house;
        this.storage = storage;
        this.user = user;

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Search"));
        top.add(search);
        top.add(filter);
        top.add(button("Refresh", this::refresh));
        top.add(balance);
        search.addActionListener(e -> refresh());
        filter.addActionListener(e -> refresh());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(button("Place bid", this::placeBid));
        actions.add(button("Sell an item", this::newListing));
        actions.add(button("Bid history", this::history));
        actions.add(button("Add funds", this::addFunds));

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(22);
        int[] widths = {35, 230, 100, 80, 130, 100, 45, 80, 70};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);
        setSize(900, 480);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        new Timer(5000, e -> refresh()).start();  // close finished auctions and refresh countdowns
        refresh();
    }

    private interface Action {
        void run() throws Exception;
    }

    private JButton button(String label, Action action) {
        JButton b = new JButton(label);
        b.addActionListener(e -> {
            try {
                action.run();
            } catch (AuctionException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Not allowed", JOptionPane.WARNING_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Unexpected error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        return b;
    }

    private void refresh() {
        boolean changed = house.closeExpired() > 0;
        model.setRows(house.search(search.getText(), filter.getSelectedIndex() == 0));
        balance.setText(String.format("   Balance %s  (available %s)", Money.format(user.balanceCents()),
                Money.format(user.availableCents())));
        if (changed) {
            save();
        }
    }

    private Auction selected() throws AuctionException {
        int row = table.getSelectedRow();
        if (row < 0) {
            throw new AuctionException("Select an auction first");
        }
        return model.at(table.convertRowIndexToModel(row));
    }

    private void placeBid() throws AuctionException {
        Auction a = selected();
        String input = JOptionPane.showInputDialog(this, "Your bid for \"" + a.title() + "\" (minimum "
                + Money.format(a.minimumNextBidCents()) + "):", Money.format(a.minimumNextBidCents()));
        if (input != null) {
            house.bid(a.id(), user.username(), Money.parse(input));
            save();
            refresh();
        }
    }

    private void newListing() throws AuctionException {
        JTextField title = new JTextField(), category = new JTextField(), price = new JTextField("100.00"),
                step = new JTextField("10.00"), hours = new JTextField("24");
        JComboBox<String> condition = new JComboBox<>(new String[] {"New", "Like new", "Used", "For parts"});
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        for (Object[] row : new Object[][] {{"Title", title}, {"Category", category}, {"Condition", condition},
                {"Starting price", price}, {"Minimum increment", step}, {"Duration (hours)", hours}}) {
            form.add(new JLabel((String) row[0]));
            form.add((java.awt.Component) row[1]);
        }
        if (JOptionPane.showConfirmDialog(this, form, "Sell an item", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            long h;
            try {
                h = Long.parseLong(hours.getText().trim());
            } catch (NumberFormatException e) {
                throw new AuctionException("Duration must be a whole number of hours");
            }
            house.list(user.username(), title.getText(), category.getText(), (String) condition.getSelectedItem(),
                    Money.parse(price.getText()), Money.parse(step.getText()), Duration.ofHours(h));
            save();
            refresh();
        }
    }

    private void history() throws AuctionException {
        Auction a = selected();
        StringBuilder sb = new StringBuilder(a.title() + " - seller " + a.seller() + "\n\n");
        if (a.bids().isEmpty()) {
            sb.append("No bids yet.");
        }
        a.bids().forEach(b -> sb.append(String.format("%-14s %10s   %s%n", b.bidder(), Money.format(b.amountCents()), b.at())));
        JOptionPane.showMessageDialog(this, sb.toString(), "Bid history", JOptionPane.INFORMATION_MESSAGE);
    }

    private void addFunds() throws AuctionException {
        String input = JOptionPane.showInputDialog(this, "Amount to add:", "100.00");
        if (input != null) {
            user.deposit(Money.parse(input));
            save();
            refresh();
        }
    }

    private void save() {
        try {
            storage.save(house);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not save: " + e.getMessage(), "Save failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Table of auctions with live "time left" and current price columns. */
    static final class AuctionTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {"#", "Item", "Category", "Seller", "Current bid", "Next minimum", "Bids", "Time left", "Status"};
        private transient List<Auction> rows = List.of();

        void setRows(List<Auction> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        Auction at(int row) { return rows.get(row); }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int c) { return COLUMNS[c]; }

        @Override
        public Object getValueAt(int r, int c) {
            Auction a = rows.get(r);
            return switch (c) {
                case 0 -> a.id();
                case 1 -> a.title();
                case 2 -> a.category();
                case 3 -> a.seller();
                case 4 -> a.leadingBid().map(b -> Money.format(b.amountCents()) + " (" + b.bidder() + ")").orElse("-");
                case 5 -> a.status() == Auction.Status.OPEN ? Money.format(a.minimumNextBidCents()) : "-";
                case 6 -> a.bids().size();
                case 7 -> timeLeft(a.endsAt());
                default -> a.status();
            };
        }

        static String timeLeft(Instant end) {
            Duration d = Duration.between(Instant.now(), end);
            if (d.isNegative() || d.isZero()) {
                return "ended";
            }
            return d.toDays() > 0 ? d.toDays() + "d " + d.toHoursPart() + "h" : d.toHoursPart() + "h " + d.toMinutesPart() + "m";
        }
    }

    // ---- login ---------------------------------------------------------------------------------------------

    static User signIn(AuctionHouse house, Storage storage) {
        while (true) {
            JTextField name = new JTextField();
            JPasswordField pass = new JPasswordField();
            JPanel p = new JPanel(new GridLayout(0, 1, 4, 4));
            p.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            p.add(new JLabel("Username"));
            p.add(name);
            p.add(new JLabel("Password"));
            p.add(pass);
            Object[] options = {"Log in", "Register", "Quit"};
            int choice = JOptionPane.showOptionDialog(null, p, "Online Auction", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
            char[] pw = pass.getPassword();
            try {
                if (choice == 0) {
                    return house.login(name.getText(), pw);
                } else if (choice == 1) {
                    User u = house.register(name.getText(), pw, 0);
                    storage.save(house);
                    JOptionPane.showMessageDialog(null, "Account created. Use \"Add funds\" before bidding.");
                    return u;
                } else {
                    System.exit(0);
                }
            } catch (AuctionException | IOException e) {
                JOptionPane.showMessageDialog(null, e.getMessage(), "Online Auction", JOptionPane.WARNING_MESSAGE);
            } finally {
                Arrays.fill(pw, '\0');
            }
        }
    }

    public static void main(String[] args) throws Exception {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // default look and feel
        }
        Storage storage = new Storage(Path.of(args.length > 0 ? args[0] : "auction-data.bin"));
        AuctionHouse house = storage.loadOrCreate();
        SwingUtilities.invokeLater(() -> new AuctionApp(house, storage, signIn(house, storage)).setVisible(true));
    }
}
