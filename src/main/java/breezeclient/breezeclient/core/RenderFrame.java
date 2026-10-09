package breezeclient.breezeclient.core;

/** World-space drawing surface handed to modules. All draws go through walls. Implemented in the client source set. */
public interface RenderFrame {
	void drawBox(double x1, double y1, double z1, double x2, double y2, double z2, int argb);

	void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, int argb);

	void drawText(double x, double y, double z, String text, int argb);
}
