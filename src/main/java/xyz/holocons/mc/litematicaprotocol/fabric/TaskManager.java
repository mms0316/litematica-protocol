package xyz.holocons.mc.litematicaprotocol.fabric;

import java.util.ArrayList;
import java.util.List;

public class TaskManager {
    private static final TaskManager INSTANCE = new TaskManager();

    private final List<ITask> taskList = new ArrayList<>();

    public static TaskManager getInstance() {
        return INSTANCE;
    }

    public void register(ITask task)  {
        this.taskList.add(task);
    }

    public void tick() {
        List<ITask> removalList = null;

        for (var task : taskList) {
            int offset = task.getTickOffset();
            if (offset > 0) {
                task.setTickOffset(offset - 1);
                continue;
            }

            task.run();

            if (removalList == null)
                removalList = new ArrayList<>();

            removalList.add(task);
        }

        if (removalList != null)
            for (var removal : removalList)
                taskList.remove(removal);
    }

    public int getTopTickOffset() {
        int topOffset = 0;
        for (var task : taskList) {
            int offset = task.getTickOffset();
            if (offset > topOffset) {
                topOffset = offset;
            }
        }
        return topOffset;
    }
}
