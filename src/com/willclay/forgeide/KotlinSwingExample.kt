package com.willclay.forgeide

import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JFrame

fun main() {
    val frame = JFrame("Kotlin Swing Example").apply {
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE

        setLocationRelativeTo(null)
        size = Dimension(300, 200)

        layout = FlowLayout()

        add(JButton("OK").apply {
            addActionListener { println("OK clicked") }
        })

        add(JButton("Cancel").apply {
            addActionListener { println("Cancel clicked") }
        })

        isVisible = true
    }
}