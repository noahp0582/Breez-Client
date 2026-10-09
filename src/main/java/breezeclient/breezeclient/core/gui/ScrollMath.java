package breezeclient.breezeclient.core.gui;

/** Scrolling arithmetic shared by GUI windows. */
public final class ScrollMath {
	private ScrollMath() {}

	public static double maxScroll(double contentHeight, double viewHeight) { return Math.max(0, contentHeight - viewHeight); }

	public static double clamp(double scroll, double contentHeight, double viewHeight) {
		return Math.max(0, Math.min(maxScroll(contentHeight, viewHeight), scroll));
	}

	/** Height of the scroll bar thumb in pixels (at least {@code minThumb}); full track when nothing scrolls. */
	public static double thumbHeight(double contentHeight, double viewHeight, double track, double minThumb) {
		if (contentHeight <= viewHeight || contentHeight <= 0) return track;
		return Math.max(minThumb, Math.min(track, track * viewHeight / contentHeight));
	}

	/** Y offset of the thumb inside the track for the given scroll position. */
	public static double thumbOffset(double scroll, double contentHeight, double viewHeight, double track, double thumb) {
		double max = maxScroll(contentHeight, viewHeight);
		return max == 0 ? 0 : (track - thumb) * Math.max(0, Math.min(1, scroll / max));
	}

	/** Scroll position for a thumb dragged to {@code thumbTop}. */
	public static double scrollForThumb(double thumbTop, double contentHeight, double viewHeight, double track, double thumb) {
		double span = track - thumb;
		if (span <= 0) return 0;
		return clamp(thumbTop / span * maxScroll(contentHeight, viewHeight), contentHeight, viewHeight);
	}
}
