package fr.tropicube.docker.client;

import fr.tropicube.docker.model.ServerTemplate;
import org.junit.jupiter.api.Test;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class MemoryBudgetTest {
    @Test void separatesHeapAndNativeMemory() {
        ServerTemplate template = new ServerTemplate();
        template.setMaxRam(1024);
        assertEquals(1536, template.getContainerMemoryMiB());
        template.setMaxRam(4097);
        assertEquals(5122, template.getContainerMemoryMiB());
        template.setMemoryOverheadMiB(768);
        assertEquals(4865, template.getContainerMemoryMiB());
    }

    @Test void restoresOverBudgetAndReleasesIdempotently() {
        MemoryBudget budget = new MemoryBudget(100);
        budget.restore("existing", 200);
        assertThrows(MemoryBudget.CapacityExceededException.class, () -> budget.reserve("new", 1));
        budget.release("existing");
        budget.release("existing");
        budget.reserve("new", 100);
        assertEquals(100, budget.reservedMiB());
        assertThrows(IllegalStateException.class, () -> budget.reserve("new", 100));
    }

    @Test void concurrentCreationsCannotOverbook() {
        MemoryBudget budget = new MemoryBudget(10);
        AtomicInteger admitted = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 100; i++) {
                String id = "instance-" + i;
                executor.submit(() -> {
                    try { budget.reserve(id, 1); admitted.incrementAndGet(); }
                    catch (MemoryBudget.CapacityExceededException expected) { }
                });
            }
        }
        assertEquals(10, admitted.get());
        assertEquals(10, budget.reservedMiB());
    }
}
