import java.util.scanner;

public class Main
{
    public static void main(String[] args)
    {
        public static void main(String[] args) {
        // 2. Create a Scanner object connected to the system console
        Scanner scanner = new Scanner(System.in);

        // 3. Get text input
        System.out.print("Enter your name: ");
        String name = scanner.nextLine(); // Reads a full line of text

        // 4. Get numeric input
        System.out.print("Enter your age: ");
        int age = scanner.nextInt(); // Reads an integer

        // 5. Output the collected data
        System.out.println("Hello " + name + "! You are " + age + " years old.");

        // 6. Close the scanner to release resources
        scanner.close();
    }
    }
}
