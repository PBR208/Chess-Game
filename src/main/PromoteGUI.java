package main;

import javax.swing.*;
import java.awt.*;

public class PromoteGUI extends JDialog {

    public enum Choice {
        QUEEN, ROOK, BISHOP, KNIGHT
    }

    private Choice choice;

    public PromoteGUI(JFrame parent) {
        super(parent, true);

        setLayout(new GridLayout(1, 4));
        setUndecorated(true);

        JButton queen = new JButton("Queen");
        JButton rook = new JButton("Rook");
        JButton bishop = new JButton("Bishop");
        JButton knight = new JButton("Knight");

        queen.addActionListener(e -> {choice = Choice.QUEEN; dispose(); });
        rook.addActionListener(e -> { choice = Choice.ROOK; dispose(); });
        bishop.addActionListener(e -> { choice = Choice.BISHOP; dispose(); });
        knight.addActionListener(e -> { choice = Choice.KNIGHT; dispose(); });

        // adding buttons

        add(queen);
        add(rook);
        add(bishop);
        add(knight);

        pack();
        setLocationRelativeTo(parent);
    }

    public Choice showDialog() {
        setVisible(true);
        return choice;
    }
}