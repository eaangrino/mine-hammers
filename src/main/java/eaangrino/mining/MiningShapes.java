package eaangrino.mining;

import net.minecraft.core.Direction;

import java.util.List;

public final class MiningShapes {
	public static final String THREE_BY_THREE = "three_by_three";
	public static final String TUNNEL_3X1 = "tunnel_3x1";
	public static final String TUNNEL_3X1_HORIZONTAL = "tunnel_3x1_horizontal";

	private static final List<String> ORDERED_SHAPES = List.of(
			THREE_BY_THREE,
			TUNNEL_3X1,
			TUNNEL_3X1_HORIZONTAL
	);

	private MiningShapes() {
	}

	public static String sanitize(String shapeId, int legacyRadius) {
		if ("five_by_five".equals(shapeId) || "tunnel_5x3".equals(shapeId)) {
			return THREE_BY_THREE;
		}

		if (ORDERED_SHAPES.contains(shapeId)) {
			return shapeId;
		}

		return THREE_BY_THREE;
	}

	public static String cycle(String currentShape, int step) {
		String sanitizedCurrent = sanitize(currentShape, 1);
		int currentIndex = ORDERED_SHAPES.indexOf(sanitizedCurrent);
		int nextIndex = Math.floorMod(currentIndex + (step >= 0 ? 1 : -1), ORDERED_SHAPES.size());
		return ORDERED_SHAPES.get(nextIndex);
	}

	public static PlaneRange getRange(String shapeId, Direction.Axis axis) {
		String shape = sanitize(shapeId, 1);
		return switch (shape) {
			case TUNNEL_3X1 -> switch (axis) {
				// Vertical tunnel line in the mining plane.
				case X -> new PlaneRange(-1, 1, 0, 0); // y varies
				case Z -> new PlaneRange(0, 0, -1, 1); // y varies
				case Y -> new PlaneRange(0, 0, -1, 1);
			};
			case TUNNEL_3X1_HORIZONTAL -> switch (axis) {
				// Horizontal tunnel line in the mining plane.
				case X -> new PlaneRange(0, 0, -1, 1); // z varies
				case Z -> new PlaneRange(-1, 1, 0, 0); // x varies
				case Y -> new PlaneRange(-1, 1, 0, 0);
			};
			default -> new PlaneRange(-1, 1, -1, 1);
		};
	}

	public record PlaneRange(
			int firstMin,
			int firstMax,
			int secondMin,
			int secondMax
	) {
	}
}
