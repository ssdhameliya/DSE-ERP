package org.example.documentstudio.model;

/**
 * One normalized editable vector-path command. Coordinates are stored from the
 * top-left and normalized to the owning TemplateElement's width/height so the
 * path scales cleanly when the user edits its geometry.
 */
public class PathCommand {
    private String type = "M";
    private double x1;
    private double y1;
    private double x2;
    private double y2;
    private double x3;
    private double y3;

    public PathCommand() { }

    public static PathCommand move(double x, double y) { return command("M", x, y, 0, 0, 0, 0); }
    public static PathCommand line(double x, double y) { return command("L", x, y, 0, 0, 0, 0); }
    public static PathCommand curve(double c1x, double c1y, double c2x, double c2y, double x, double y) {
        return command("C", c1x, c1y, c2x, c2y, x, y);
    }
    public static PathCommand close() { return command("Z", 0, 0, 0, 0, 0, 0); }

    private static PathCommand command(String type, double x1, double y1, double x2, double y2, double x3, double y3) {
        PathCommand c = new PathCommand();
        c.type = type; c.x1 = x1; c.y1 = y1; c.x2 = x2; c.y2 = y2; c.x3 = x3; c.y3 = y3;
        return c;
    }

    public PathCommand copy() { return command(type, x1, y1, x2, y2, x3, y3); }
    public String getType() { return type == null ? "M" : type; }
    public void setType(String type) { this.type = type == null ? "M" : type.trim().toUpperCase(); }
    public double getX1() { return x1; } public void setX1(double x1) { this.x1 = x1; }
    public double getY1() { return y1; } public void setY1(double y1) { this.y1 = y1; }
    public double getX2() { return x2; } public void setX2(double x2) { this.x2 = x2; }
    public double getY2() { return y2; } public void setY2(double y2) { this.y2 = y2; }
    public double getX3() { return x3; } public void setX3(double x3) { this.x3 = x3; }
    public double getY3() { return y3; } public void setY3(double y3) { this.y3 = y3; }
}
