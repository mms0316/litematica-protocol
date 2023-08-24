package xyz.holocons.mc.litematicaprotocol;

public final class Constants {

    public static final String NAMESPACE = "litematicaprotocol";
    public static final String MAIN = "main";
    public static final String SPONGE_SCHEMATIC = "0";
    public static final String SPONGE_SCHEMATIC_SPLIT = "1";

    public static final int MAX_PAYLOAD_LENGTH = 32767;
    public static final int MAX_TOTAL_PAYLOAD_LENGTH = 2 * (1 << 16);

    public static final int PACKET_TICK_INTERVAL = 1;

    private Constants() {
    }
}
