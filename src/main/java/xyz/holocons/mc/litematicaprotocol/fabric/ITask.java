package xyz.holocons.mc.litematicaprotocol.fabric;

public interface ITask {

    int getTickOffset();

    void setTickOffset(int offset);

    void run();
}
