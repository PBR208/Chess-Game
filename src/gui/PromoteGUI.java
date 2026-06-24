package gui;

import javax.swing.*;
import java.awt.*;

public class PromoteGUI extends JDialog {

    public enum Choice {
        QUEEN, ROOK, BISHOP, KNIGHT
    }

    private Choice choice;

    public PromoteGUI(JFrame parent, int tileSize) {
        super(parent, true);

        int gap = tileSize / 8;
        setLayout(new GridLayout(1, 4, gap, gap));
        setUndecorated(true);

        Font buttonFont = new Font("Arial", Font.BOLD, tileSize / 5);

        JButton queen = new JButton("Queen");
        JButton rook = new JButton("Rook");
        JButton bishop = new JButton("Bishop");
        JButton knight = new JButton("Knight");

        for (JButton btn : new JButton[]{queen, rook, bishop, knight}) {
            btn.setFont(buttonFont);
        }

        queen.addActionListener(e -> {
            choice = Choice.QUEEN;
            dispose();
        });
        rook.addActionListener(e -> {
            choice = Choice.ROOK;
            dispose();
        });
        bishop.addActionListener(e -> {
            choice = Choice.BISHOP;
            dispose();
        });
        knight.addActionListener(e -> {
            choice = Choice.KNIGHT;
            dispose();
        });

        // adding buttons

        add(queen);
        add(rook);
        add(bishop);
        add(knight);

        setSize(tileSize * 4, tileSize);
        setLocationRelativeTo(parent);
    }

    public Choice showDialog() {
        setVisible(true);
        return choice;
    }
}