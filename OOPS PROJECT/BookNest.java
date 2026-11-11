import java.util.*;
import java.sql.*;

class Book {
    private String id;
    private String title;
    private String author;
    private int totalCopies;
    private int availableCopies;

    public Book(String id, String title, String author, int totalCopies) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.totalCopies = totalCopies;
        this.availableCopies = totalCopies;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public int getTotalCopies() {
        return totalCopies;
    }

    public int getAvailableCopies() {
        return availableCopies;
    }

    public void setAvailableCopies(int availableCopies) {
        this.availableCopies = availableCopies;
    }
}

class Member {
    private String id;
    private String name;

    public Member(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}

class Library {
    private Connection con;
    private static int totalBooksIssued = 0;

    public Library() throws SQLException {
        String url = "jdbc:mysql://localhost:3306/";
        String user = "root";
        String password = "Sanjeev@2021";

        Connection firstConnection = DriverManager.getConnection(url, user, password);
        Statement st = firstConnection.createStatement();
        st.executeUpdate("CREATE DATABASE IF NOT EXISTS booknest");
        st.close();
        firstConnection.close();

        con = DriverManager.getConnection(url + "booknest", user, password);
        createTables();
        loadIssuedCount();
    }

    private void createTables() throws SQLException {
        Statement st = con.createStatement();
        st.executeUpdate("CREATE TABLE IF NOT EXISTS books (id VARCHAR(30) PRIMARY KEY, title VARCHAR(150) NOT NULL, author VARCHAR(100) NOT NULL, total_copies INT NOT NULL, available_copies INT NOT NULL)");
        st.executeUpdate("CREATE TABLE IF NOT EXISTS members (id VARCHAR(30) PRIMARY KEY, name VARCHAR(100) NOT NULL)");
        st.executeUpdate("CREATE TABLE IF NOT EXISTS borrow_records (record_id INT AUTO_INCREMENT PRIMARY KEY, member_id VARCHAR(30) NOT NULL, book_id VARCHAR(30) NOT NULL, active TINYINT NOT NULL DEFAULT 1, FOREIGN KEY (member_id) REFERENCES members(id), FOREIGN KEY (book_id) REFERENCES books(id))");
        st.close();
    }

    private void loadIssuedCount() throws SQLException {
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM borrow_records");
        if (rs.next()) {
            totalBooksIssued = rs.getInt(1);
        }
        rs.close();
        st.close();
    }

    public void addBook(Book book) throws SQLException {
        String sql = "INSERT INTO books VALUES (?, ?, ?, ?, ?)";
        PreparedStatement ps = con.prepareStatement(sql);
        ps.setString(1, book.getId());
        ps.setString(2, book.getTitle());
        ps.setString(3, book.getAuthor());
        ps.setInt(4, book.getTotalCopies());
        ps.setInt(5, book.getAvailableCopies());

        try {
            ps.executeUpdate();
            System.out.println("Book added successfully.");
        } catch (SQLIntegrityConstraintViolationException e) {
            System.out.println("Book ID already exists.");
        } finally {
            ps.close();
        }
    }

    public void addMember(Member member) throws SQLException {
        String sql = "INSERT INTO members VALUES (?, ?)";
        PreparedStatement ps = con.prepareStatement(sql);
        ps.setString(1, member.getId());
        ps.setString(2, member.getName());

        try {
            ps.executeUpdate();
            System.out.println("Member registered successfully.");
        } catch (SQLIntegrityConstraintViolationException e) {
            System.out.println("Member ID already exists.");
        } finally {
            ps.close();
        }
    }

    public void issueBook(String memberId, String bookId) throws SQLException {
        PreparedStatement ps = con.prepareStatement("SELECT name FROM members WHERE id = ?");
        ps.setString(1, memberId);
        ResultSet memberResult = ps.executeQuery();

        if (!memberResult.next()) {
            System.out.println("Member not found.");
            memberResult.close();
            ps.close();
            return;
        }

        String memberName = memberResult.getString("name");
        memberResult.close();
        ps.close();

        ps = con.prepareStatement("SELECT title, available_copies FROM books WHERE id = ?");
        ps.setString(1, bookId);
        ResultSet bookResult = ps.executeQuery();

        if (!bookResult.next()) {
            System.out.println("Book not found.");
            bookResult.close();
            ps.close();
            return;
        }

        String title = bookResult.getString("title");
        int available = bookResult.getInt("available_copies");
        bookResult.close();
        ps.close();

        if (available <= 0) {
            System.out.println("No copies available.");
            return;
        }

        ps = con.prepareStatement("SELECT COUNT(*) FROM borrow_records WHERE member_id = ? AND active = 1");
        ps.setString(1, memberId);
        ResultSet countResult = ps.executeQuery();
        countResult.next();
        int borrowed = countResult.getInt(1);
        countResult.close();
        ps.close();

        if (borrowed >= 3) {
            System.out.println("Member has already borrowed 3 books.");
            return;
        }

        con.setAutoCommit(false);

        try {
            ps = con.prepareStatement("UPDATE books SET available_copies = available_copies - 1 WHERE id = ? AND available_copies > 0");
            ps.setString(1, bookId);
            int updated = ps.executeUpdate();
            ps.close();

            if (updated == 0) {
                con.rollback();
                System.out.println("No copies available.");
                return;
            }

            ps = con.prepareStatement("INSERT INTO borrow_records (member_id, book_id, active) VALUES (?, ?, 1)");
            ps.setString(1, memberId);
            ps.setString(2, bookId);
            ps.executeUpdate();
            ps.close();

            con.commit();
            totalBooksIssued++;
            System.out.println("Book \"" + title + "\" issued to " + memberName + ". Copies left: " + (available - 1));
        } catch (SQLException e) {
            con.rollback();
            System.out.println("Unable to issue book.");
        } finally {
            con.setAutoCommit(true);
        }
    }

    public void returnBook(String memberId, String bookId, int daysKept) throws SQLException {
        if (daysKept < 0) {
            System.out.println("Days kept cannot be negative.");
            return;
        }

        PreparedStatement ps = con.prepareStatement("SELECT record_id FROM borrow_records WHERE member_id = ? AND book_id = ? AND active = 1 LIMIT 1");
        ps.setString(1, memberId);
        ps.setString(2, bookId);
        ResultSet rs = ps.executeQuery();

        if (!rs.next()) {
            System.out.println("No active borrowing record found.");
            rs.close();
            ps.close();
            return;
        }

        int recordId = rs.getInt("record_id");
        rs.close();
        ps.close();

        con.setAutoCommit(false);

        try {
            ps = con.prepareStatement("UPDATE borrow_records SET active = 0 WHERE record_id = ?");
            ps.setInt(1, recordId);
            ps.executeUpdate();
            ps.close();

            ps = con.prepareStatement("UPDATE books SET available_copies = available_copies + 1 WHERE id = ?");
            ps.setString(1, bookId);
            ps.executeUpdate();
            ps.close();

            con.commit();

            int lateDays = Math.max(0, daysKept - 7);
            int fine = lateDays * 2;

            if (lateDays > 0) {
                System.out.println("Late by " + lateDays + " days. Fine = Rs. " + fine);
            } else {
                System.out.println("No fine.");
            }

            System.out.println("Book returned successfully.");
        } catch (SQLException e) {
            con.rollback();
            System.out.println("Unable to return book.");
        } finally {
            con.setAutoCommit(true);
        }
    }

    public void searchBook(String id) throws SQLException {
        PreparedStatement ps = con.prepareStatement("SELECT * FROM books WHERE id = ?");
        ps.setString(1, id);
        showSearchResults(ps);
    }

    public void searchBook(String keyword, boolean byTitle) throws SQLException {
        PreparedStatement ps;

        if (byTitle) {
            ps = con.prepareStatement("SELECT * FROM books WHERE title LIKE ?");
            ps.setString(1, "%" + keyword + "%");
        } else {
            ps = con.prepareStatement("SELECT * FROM books WHERE author LIKE ?");
            ps.setString(1, "%" + keyword + "%");
        }

        showSearchResults(ps);
    }

    private void showSearchResults(PreparedStatement ps) throws SQLException {
        ResultSet rs = ps.executeQuery();
        boolean found = false;

        while (rs.next()) {
            found = true;
            System.out.println("Book ID: " + rs.getString("id"));
            System.out.println("Title: " + rs.getString("title"));
            System.out.println("Author: " + rs.getString("author"));
            System.out.println("Total copies: " + rs.getInt("total_copies"));
            System.out.println("Available copies: " + rs.getInt("available_copies"));
            System.out.println();
        }

        if (!found) {
            System.out.println("No books found.");
        }

        rs.close();
        ps.close();
    }

    public void showAll() throws SQLException {
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery("SELECT * FROM books");

        while (rs.next()) {
            System.out.println("Book ID: " + rs.getString("id"));
            System.out.println("Title: " + rs.getString("title"));
            System.out.println("Author: " + rs.getString("author"));
            System.out.println("Total copies: " + rs.getInt("total_copies"));
            System.out.println("Available copies: " + rs.getInt("available_copies"));
            System.out.println();
        }

        rs.close();
        st.close();

        st = con.createStatement();
        rs = st.executeQuery("SELECT COUNT(*) FROM members");
        rs.next();
        System.out.println("Total registered members: " + rs.getInt(1));
        System.out.println("Total books issued: " + totalBooksIssued);
        rs.close();
        st.close();
    }

    public void close() throws SQLException {
        if (con != null) {
            con.close();
        }
    }
}

public class BookNest {
    private static Scanner sc = new Scanner(System.in);

    private static String readText(String message) {
        while (true) {
            System.out.print(message);
            String value = sc.nextLine().trim();

            if (!value.isEmpty()) {
                return value;
            }

            System.out.println("Input cannot be empty.");
        }
    }

    private static int readNumber(String message) {
        while (true) {
            System.out.print(message);
            String value = sc.nextLine().trim();

            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    public static void main(String[] args) {
        Library library = null;

        try {
            library = new Library();
            int choice;

            do {
                System.out.println("\n===== BookNest Library =====");
                System.out.println("1. Add Book");
                System.out.println("2. Add Member");
                System.out.println("3. Issue Book");
                System.out.println("4. Return Book");
                System.out.println("5. Search");
                System.out.println("6. Show All");
                System.out.println("7. Exit");

                choice = readNumber("Enter choice: ");

                try {
                    switch (choice) {
                        case 1:
                            String bookId = readText("Enter Book ID: ");
                            String title = readText("Enter title: ");
                            String author = readText("Enter author: ");
                            int copies;

                            do {
                                copies = readNumber("Enter number of copies: ");
                                if (copies < 0) {
                                    System.out.println("Copies cannot be negative.");
                                }
                            } while (copies < 0);

                            library.addBook(new Book(bookId, title, author, copies));
                            break;

                        case 2:
                            String memberId = readText("Enter Member ID: ");
                            String memberName = readText("Enter member name: ");
                            library.addMember(new Member(memberId, memberName));
                            break;

                        case 3:
                            String issueMemberId = readText("Enter Member ID: ");
                            String issueBookId = readText("Enter Book ID: ");
                            library.issueBook(issueMemberId, issueBookId);
                            break;

                        case 4:
                            String returnMemberId = readText("Enter Member ID: ");
                            String returnBookId = readText("Enter Book ID: ");
                            int days;

                            do {
                                days = readNumber("Days kept: ");
                                if (days < 0) {
                                    System.out.println("Days cannot be negative.");
                                }
                            } while (days < 0);

                            library.returnBook(returnMemberId, returnBookId, days);
                            break;

                        case 5:
                            System.out.println("1. Search by Book ID");
                            System.out.println("2. Search by Title");
                            System.out.println("3. Search by Author");
                            int searchChoice = readNumber("Enter choice: ");

                            if (searchChoice == 1) {
                                library.searchBook(readText("Enter Book ID: "));
                            } else if (searchChoice == 2) {
                                library.searchBook(readText("Enter title keyword: "), true);
                            } else if (searchChoice == 3) {
                                library.searchBook(readText("Enter author keyword: "), false);
                            } else {
                                System.out.println("Invalid search choice.");
                            }
                            break;

                        case 6:
                            library.showAll();
                            break;

                        case 7:
                            System.out.println("Thank you for using BookNest.");
                            break;

                        default:
                            System.out.println("Invalid choice. Enter a number from 1 to 7.");
                    }
                } catch (SQLException e) {
                    System.out.println("Database operation failed: " + e.getMessage());
                }
            } while (choice != 7);
        } catch (SQLException e) {
            System.out.println("Database connection failed: " + e.getMessage());
            System.out.println("Check MySQL is running, the password is correct, and the JDBC driver is installed.");
        } finally {
            try {
                if (library != null) {
                    library.close();
                }
            } catch (SQLException e) {
                System.out.println("Unable to close database connection.");
            }
            sc.close();
        }
    }
}
