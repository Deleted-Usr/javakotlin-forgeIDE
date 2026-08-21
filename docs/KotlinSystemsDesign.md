## Kotlin / Rust Systems Design

### Kotlin - 

Since Kotlin and Java both run on the JVM, and both compile down to the same bytecode, they're interchangeable. This
is especially useful for boilerplate and very repetitive code. As well, since they're interchangeable, classes from
Javax.Swing and Java.AWT can be called directly from within Kotlin, though they were directly developed for Java. 
The following class is a Java example of a simple JButton with a flow layout:

```java
public class SwingExample 
{
    public static void main(String[] args) 
    {
        JFrame frame = new JFrame("Java Swing Example");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(300, 200);

        JButton button = new JButton("Click Me");
        button.addActionListener(e -> System.out.println("Button clicked!"));

        frame.setLayout(new FlowLayout());
        frame.add(button);
        frame.setVisible(true);
    }
}
```

The Kotlin equivalent to this is:
```kotlin
fun main() 
{
    val frame = JFrame("Kotlin Swing Example").apply 
    {
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        setSize(300, 200)

        layout = FlowLayout()
        add(JButton("Click Me").apply 
        {
            addActionListener { println("Button clicked!") }
        })

        isVisible = true
    }
}
```

The resulting Kotlin code is much simpler to read and understand from a developer's perspective, as Kotlin's use of
lambda expressions reduces the scope of variables to what is necessary.

| Feature               | Java                                                       | Kotlin                                                          |
|-----------------------|------------------------------------------------------------|-----------------------------------------------------------------|
| Boilerplate           | More verbose (explicit `new`, semicolons, getters/setters) | More concise (no `new`, no semicolons, property access syntax   |
| Lambdas               | Java 8+ supports lambdas but is still more verbose         | Lambdas are first-class and shorter                             |
| Proptery Access       | `frame.setSize(300, 200)`                                  | `frame.setSize(300, 200)` or `frame.size = Dimension(300, 200)` |
| Object Initialisation | Multiple lines                                             | `apply { ... }` or also `{ ... }` for inline configuration      | 
| Null Safety           | No built-in null safety                                    | Built-in null safety (`?`, `!!`)                                |

Advanced Swing UI utilises a DSL-like style built into Kotlin:
```kotlin
fun main() 
{
    JFrame("Kotlin Swing DSL Style").apply 
    {
        defaultCloseOperation = EXIT_ON_CLOSE
        size = Dimension(300, 200)
        layout = FlowLayout()

        add(JButton("OK").apply 
        {
            addActionListener { println("OK clicked") }
        })

        add(JButton("Cancel").apply 
        {
            addActionListener { println("Cancel clicked") }
        })

        isVisible = true
    }
}
```