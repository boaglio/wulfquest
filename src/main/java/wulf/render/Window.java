package wulf.render;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.RenderingHints;
import java.awt.Graphics2D;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import javax.swing.JFrame;
import javax.swing.WindowConstants;

/**
 * The host window (AGENTS.md §5.3): one {@code drawImage} of the upscaled
 * framebuffer per frame, nearest-neighbour, letterboxed in the border colour.
 */
public final class Window {

    private final JFrame frame;
    private final Canvas canvas;
    private BufferStrategy strategy;

    public Window(String title, int width, int height, Color letterbox) {
        this.canvas = new Canvas();
        canvas.setPreferredSize(new Dimension(width, height));
        canvas.setBackground(letterbox);
        canvas.setFocusable(true);
        canvas.setIgnoreRepaint(true);

        this.frame = new JFrame(title);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setResizable(true);
        frame.setBackground(letterbox);
        frame.add(canvas);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        canvas.createBufferStrategy(2);
        this.strategy = canvas.getBufferStrategy();
        canvas.requestFocusInWindow();
    }

    public JFrame frame() {
        return frame;
    }

    public Canvas canvas() {
        return canvas;
    }

    public boolean open() {
        return frame.isDisplayable();
    }

    /** Blits the image centred, at the largest integer scale that still fits. */
    public void present(BufferedImage image) {
        if (strategy == null) {
            strategy = canvas.getBufferStrategy();
            if (strategy == null) {
                return;
            }
        }
        do {
            Graphics g = strategy.getDrawGraphics();
            try {
                int cw = Math.max(1, canvas.getWidth());
                int ch = Math.max(1, canvas.getHeight());
                g.setColor(canvas.getBackground());
                g.fillRect(0, 0, cw, ch);
                if (g instanceof Graphics2D g2) {
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                }
                int x = (cw - image.getWidth()) / 2;
                int y = (ch - image.getHeight()) / 2;
                g.drawImage(image, x, y, null);
            } finally {
                g.dispose();
            }
            strategy.show();
        } while (strategy.contentsLost());
    }

    public void close() {
        frame.dispose();
    }
}
