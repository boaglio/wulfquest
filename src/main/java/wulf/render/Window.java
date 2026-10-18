package wulf.render;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.RenderingHints;
import java.awt.Graphics2D;
import java.awt.Taskbar;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.util.List;
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
    private boolean fullscreen;

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

    /**
     * §17.9: over the whole screen, or a window around a canvas of {@code width} x {@code height}.
     * Done on the event thread while no frame is drawn; the keys go on reaching the canvas.
     */
    public void setMode(boolean full, int width, int height) {
        Runnable change = () -> {
            synchronized (this) {
                java.awt.GraphicsDevice screen = frame.getGraphicsConfiguration().getDevice();
                if (full != fullscreen) {
                    if (fullscreen && screen.getFullScreenWindow() == frame) {
                        screen.setFullScreenWindow(null);
                    }
                    frame.dispose();
                    frame.setUndecorated(full);
                    fullscreen = full;
                }
                if (full) {
                    if (screen.isFullScreenSupported()) {
                        screen.setFullScreenWindow(frame);
                    } else {
                        frame.setBounds(frame.getGraphicsConfiguration().getBounds());
                    }
                } else {
                    canvas.setPreferredSize(new Dimension(width, height));
                    frame.pack();
                    frame.setLocationRelativeTo(null);
                }
                frame.setVisible(true);
                canvas.createBufferStrategy(2);
                strategy = canvas.getBufferStrategy();
                canvas.requestFocusInWindow();
            }
        };
        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            change.run();
        } else {
            try {
                javax.swing.SwingUtilities.invokeAndWait(change);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw new IllegalStateException("could not change the window", e.getCause());
            }
        }
    }

    /** The size of the screen the window is on, in pixels. */
    public Dimension screenSize() {
        java.awt.Rectangle r = frame.getGraphicsConfiguration().getBounds();
        return new Dimension(r.width, r.height);
    }

    /** The window's icon, and the dock's where there is one (macOS, when run from the jar). */
    public void setIcon(List<BufferedImage> images) {
        frame.setIconImages(images);
        if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
            Taskbar.getTaskbar().setIconImage(images.get(images.size() - 1));
        }
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

    /** Blits the image centred, letterboxed in the border colour. */
    public synchronized void present(BufferedImage image) {
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
