/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Original paw artwork; an offline tool, never included in the plugin JAR. */
public final class GenerateIcon
{
    public static void main(String[] args) throws Exception
    {
        BufferedImage icon = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = icon.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(63, 45, 90));
        graphics.fillRoundRect(0, 0, 48, 48, 12, 12);
        graphics.setColor(new Color(245, 202, 145));
        graphics.fill(new Ellipse2D.Double(8, 16, 8, 11));
        graphics.fill(new Ellipse2D.Double(17, 9, 8, 12));
        graphics.fill(new Ellipse2D.Double(27, 10, 8, 12));
        graphics.fill(new Ellipse2D.Double(35, 19, 7, 10));
        Path2D pad = new Path2D.Double();
        pad.moveTo(13, 34);
        pad.curveTo(13, 29, 20, 23, 25, 23);
        pad.curveTo(30, 23, 37, 30, 36, 35);
        pad.curveTo(35, 41, 29, 38, 25, 38);
        pad.curveTo(21, 38, 13, 41, 13, 34);
        graphics.fill(pad);
        graphics.dispose();
        ImageIO.write(icon, "png", new File("icon.png"));
    }
}
