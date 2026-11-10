package com.willclay.forgeide.ui.projects

import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.util.UIScale
import com.willclay.forgeide.application.RecentProject
import com.willclay.forgeide.ui.icons.AppIcons
import com.willclay.forgeide.ui.icons.FileIcons
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** Displays cached history only. Project opening and persistence belong to the controller. */
class RecentProjectsDialog(
    owner: JFrame,
    projects: List<RecentProject>,

    newProject: Action,
    openProject: Action,

    private val onOpen:   (RecentProject) -> Unit,
    private val onRemove: (RecentProject) -> Unit,
) : JDialog(owner, "Recent Projects", ModalityType.MODELESS) {
    private var projects = projects
    private val search   = JTextField()
    private val model    = DefaultListModel<RecentProject>()

    private val list = object : JList<RecentProject>(model) {
        override fun getScrollableTracksViewportWidth() = true
    }

    private val open   = JButton("Open Project")
    private val remove = JButton("Remove from Recent")
    private val empty  = JLabel("", SwingConstants.CENTER)
    private val cards  = JPanel(CardLayout())

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        iconImages            = AppIcons.images()
        minimumSize           = UIScale.scale(Dimension(660, 420))
        size                  = UIScale.scale(Dimension(840, 540))

        add(sidebar(newProject, openProject), BorderLayout.WEST)
        add(projectsPanel(), BorderLayout.CENTER)

        rootPane.defaultButton = open

        bind(rootPane, KeyEvent.VK_ESCAPE, "close")    { dispose() }
        bind(list,     KeyEvent.VK_DELETE, "remove")   { removeSelected() }
        bind(search,   KeyEvent.VK_DOWN,   "next")     { moveSelection(1) }
        bind(search,   KeyEvent.VK_UP,     "previous") { moveSelection(-1) }

        search.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(event: DocumentEvent)  = filter()
            override fun removeUpdate(event: DocumentEvent)  = filter()
            override fun changedUpdate(event: DocumentEvent) = filter()
        })

        addWindowListener(object : WindowAdapter() {
            override fun windowOpened(event: WindowEvent) { search.requestFocusInWindow() }
        })

        filter()
        setLocationRelativeTo(owner)
    }

    private fun sidebar(newProject: Action, openProject: Action): JComponent {
        val column = JPanel().apply {
            layout        = BoxLayout(this, BoxLayout.Y_AXIS)
            border        = BorderFactory.createEmptyBorder(28, 20, 20, 20)
            preferredSize = UIScale.scale(Dimension(230, 0))
        }

        val brand = JLabel("Forge IDE", AppIcons.images().firstOrNull { it.getWidth(null) == 32 }?.let(::ImageIcon), JLabel.LEADING).apply {
            font        = font.deriveFont(Font.BOLD, 22f)
            iconTextGap = UIScale.scale(10)
            alignmentX  = Component.LEFT_ALIGNMENT
        }

        column.add(brand)

        column.add(Box.createVerticalStrut(UIScale.scale(10)))

        column.add(Box.createVerticalStrut(UIScale.scale(32)))

        fun command(action: Action) = JButton(action).apply {
            alignmentX          = Component.LEFT_ALIGNMENT
            horizontalAlignment = SwingConstants.LEADING
            maximumSize         = Dimension(Int.MAX_VALUE, preferredSize.height)

            addActionListener { dispose() }
        }

        column.add(command(newProject))

        column.add(Box.createVerticalStrut(UIScale.scale(8)))

        column.add(command(openProject))

        column.add(Box.createVerticalGlue())

        column.add(JLabel("An IDE that knows your workflow.").apply {
            font = font.deriveFont(11f)
            putClientProperty(FlatClientProperties.STYLE, "foreground: \$Label.disabledForeground")
        })

        return JPanel(BorderLayout()).apply {
            add(column,    BorderLayout.CENTER)
            add(JSeparator(SwingConstants.VERTICAL), BorderLayout.EAST)
        }
    }

    private fun projectsPanel(): JComponent {
        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search by project name or path")
        search.accessibleContext.accessibleName = "Search recent projects"
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true)

        val heading = JPanel(BorderLayout(0, 16)).apply {
            add(JLabel("Recent Projects").apply {
                font = font.deriveFont(Font.BOLD, 20f)
            }, BorderLayout.NORTH)

            add(search, BorderLayout.SOUTH)
        }

        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.cellRenderer = ProjectCell()
        list.accessibleContext.accessibleName = "Recent projects"
        list.addListSelectionListener { updateButtons() }
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                val index = list.locationToIndex(event.point)

                if (
                    event.clickCount == 2                   &&
                    SwingUtilities.isLeftMouseButton(event) &&
                    index >= 0                              &&
                    list.getCellBounds(index, index).contains(event.point)
                ) {
                    openSelected()
                }
            }
        })

        bind(list, KeyEvent.VK_ENTER, "open") { openSelected() }

        val scroll = JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER).apply {
            border = BorderFactory.createEmptyBorder()
        }

        empty.putClientProperty(FlatClientProperties.STYLE, "foreground: \$Label.disabledForeground")

        cards.add(scroll, "projects")
        cards.add(empty,  "empty")

        remove.toolTipText = "Forget this entry; the project files are kept"
        remove.addActionListener { removeSelected() }

        open.addActionListener { openSelected() }

        val buttons = JPanel(BorderLayout()).apply {
            add(remove, BorderLayout.WEST)

            add(JPanel(FlowLayout(FlowLayout.TRAILING, 0, 0)).apply {
                add(JButton("Close").apply { addActionListener { dispose() } })
                add(Box.createHorizontalStrut(UIScale.scale(8)))
                add(open)
            }, BorderLayout.EAST)
        }

        return JPanel(BorderLayout(0, 16)).apply {
            border = BorderFactory.createEmptyBorder(24, 20, 20, 20)

            add(heading, BorderLayout.NORTH)
            add(cards,   BorderLayout.CENTER)
            add(buttons, BorderLayout.SOUTH)
        }
    }

    private fun openSelected()   { list.selectedValue?.let(onOpen) }
    private fun removeSelected() { list.selectedValue?.let(onRemove) }

    fun setProjects(updated: List<RecentProject>) {
        projects = updated
        filter()
    }

    private fun filter() {
        val selected = list.selectedValue?.root
        val query    = search.text.trim()

        model.clear()

        projects.filter {
            query.isEmpty() || it.name.contains(query, true) || it.root.toString().contains(query, true) || it.language.contains(query, true)
        }.forEach(model::addElement)

        if (!model.isEmpty) {
            list.selectedIndex = (0 until model.size()).firstOrNull { model[it].root == selected } ?: 0
        }

        empty.text = if (projects.isEmpty()) {
            "<html><center>No recent projects yet.<br>Open or create a project to get started.</center></html>"
        }
        else {
            "No matching projects."
        }

        (cards.layout as CardLayout).show(cards, if (model.isEmpty) "empty" else "projects")

        updateButtons()
    }

    private fun updateButtons() {
        open.isEnabled   = list.selectedValue != null
        remove.isEnabled = list.selectedValue != null
    }

    private fun moveSelection(delta: Int) {
        if (model.isEmpty) {
            return
        }

        list.selectedIndex = Math.clamp((list.selectedIndex + delta).toLong(), 0, model.size() - 1)
        list.ensureIndexIsVisible(list.selectedIndex)
    }

    private fun bind(component: JComponent, key: Int, name: String, action: () -> Unit) {
        component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(key, 0), name)
        component.actionMap.put(name, object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent) = action()
        })
    }

    /** Two-line cards, using the same rounded selection and theme colours as Forge's other dialogs. */
    private class ProjectCell : JPanel(BorderLayout(12, 4)), ListCellRenderer<RecentProject> {
        private val titleLabel = JLabel()
        private val path       = JLabel()
        private val language   = JLabel()
        private var selected   = false

        init {
            isOpaque = false
            border   = BorderFactory.createEmptyBorder(12, 12, 12, 12)

            add(JLabel(FileIcons.folder()), BorderLayout.WEST)
            add(JPanel(BorderLayout(0, 5)).apply {
                isOpaque = false

                add(JPanel(BorderLayout(8, 0)).apply {
                    isOpaque = false

                    add(titleLabel, BorderLayout.CENTER)
                    add(language, BorderLayout.EAST)
                }, BorderLayout.NORTH)

                add(path, BorderLayout.SOUTH)
            }, BorderLayout.CENTER)

            titleLabel.font = titleLabel.font.deriveFont(Font.BOLD)
            language.font   = language.font.deriveFont(11f)
        }

        override fun getListCellRendererComponent(
            list: JList<out RecentProject>,
            value: RecentProject,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            selected = isSelected

            titleLabel.text = value.name
            path.text       = value.root.toString()
            language.text   = value.language

            titleLabel.foreground = if (selected) {
                list.selectionForeground
            }
            else {
                list.foreground
            }

            val secondary = if (selected) {
                list.selectionForeground
            }
            else {
                UIManager.getColor("Label.disabledForeground")
            }
            path.foreground     = secondary
            language.foreground = secondary

            toolTipText = value.root.toString()
            background  = list.selectionBackground

            return this
        }

        override fun paintComponent(graphics: Graphics) {
            if (selected) {
                val copy = graphics.create() as Graphics2D

                try {
                    copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    copy.color = background

                    val gap = UIScale.scale(2)

                    copy.fillRoundRect(0, gap, width, height - gap * 2, UIScale.scale(8), UIScale.scale(8))
                }
                finally {
                    copy.dispose()
                }
            }

            super.paintComponent(graphics)
        }
    }
}
