/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Original prop-box artwork; an offline tool, excluded from the plugin JAR. */
public final class GenerateIcon
{
	public static void main(String[] args) throws Exception
	{
		BufferedImage icon = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = icon.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(63, 45, 90)); g.fillRoundRect(0, 0, 48, 48, 12, 12);
		g.setColor(new Color(255, 220, 164)); g.fill(new Polygon(new int[]{24, 40, 24, 8}, new int[]{7, 16, 25, 16}, 4));
		g.setColor(new Color(211, 162, 103)); g.fill(new Polygon(new int[]{8, 24, 24, 8}, new int[]{16, 25, 42, 33}, 4));
		g.setColor(new Color(166, 112, 72)); g.fill(new Polygon(new int[]{24, 40, 40, 24}, new int[]{25, 16, 33, 42}, 4));
		g.setColor(new Color(94, 61, 48)); g.setStroke(new BasicStroke(2f));
		g.drawLine(8, 16, 24, 25); g.drawLine(24, 25, 40, 16); g.drawLine(24, 25, 24, 42);
		g.drawLine(10, 20, 21, 35); g.drawLine(27, 35, 38, 20);
		g.dispose(); ImageIO.write(icon, "png", new File("icon.png"));
	}
}
