package xyz.holocons.mc.litematicaprotocol.fabric;

public interface ITask {

    int getTickOffset();

    void setTickOffset(int offset);

    default int getTickDuration() {
        return 0;
    }
    default void setTickDuration(int duration) {

    }

    void run();
}
