import com.memorygames.pk.admin.AdminPasswordHash;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Server-side helper: read one password line from stdin, print its bcrypt hash. */
public class GenHash {
    public static void main(String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String password = reader.readLine();
        if (password == null) {
            System.err.println("No password received on stdin.");
            System.exit(2);
        }
        try {
            System.out.println(AdminPasswordHash.hashPassword(password.toCharArray()));
        } catch (IllegalArgumentException exception) {
            System.err.println("Password rejected: use at least 12 characters and at most 72 UTF-8 bytes.");
            System.exit(2);
        }
    }
}
