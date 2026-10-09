import com.willfp.eco.core.Eco;
import com.willfp.eco.core.cache.EcoCache;
import com.willfp.eco.core.fast.FastItemStack;
import com.willfp.eco.core.items.HashedItem;
import com.willfp.eco.core.items.Items;
import com.willfp.eco.core.items.TestableItem;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CustomItemPresenceTest {
    private Field bridgeField;
    private Object previousBridge;
    private EcoCache<HashedItem, Optional<TestableItem>> cache;
    private final List<NamespacedKey> registered = new ArrayList<>();
    private final AtomicInteger wraps = new AtomicInteger();
    private final AtomicInteger matches = new AtomicInteger();
    private final AtomicInteger examples = new AtomicInteger();
    private static final AtomicInteger ids = new AtomicInteger(10000);

    private static final class Stack extends ItemStack {
        private final int id = ids.incrementAndGet();
        private final boolean meta;
        Stack(boolean meta) { this.meta = meta; }
        @Override public boolean hasItemMeta() { return meta; }
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Exception {
        bridgeField = Eco.Instance.class.getDeclaredField("eco");
        bridgeField.setAccessible(true);
        previousBridge = bridgeField.get(null);
        Eco bridge = (Eco) Proxy.newProxyInstance(Eco.class.getClassLoader(), new Class<?>[]{Eco.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("createFastItemStack")) {
                        throw new AssertionError("Presence check requested backend operation: " + method.getName());
                    }
                    wraps.incrementAndGet();
                    Stack stack = (Stack) args[0];
                    return Proxy.newProxyInstance(FastItemStack.class.getClassLoader(), new Class<?>[]{FastItemStack.class},
                            (fast, operation, arguments) -> {
                                if (operation.getName().equals("hashCode")) return stack.id;
                                throw new AssertionError("Unexpected fast item operation: " + operation.getName());
                            });
                });
        bridgeField.set(null, bridge);
        Field cacheField = Items.class.getDeclaredField("CACHE");
        cacheField.setAccessible(true);
        cache = (EcoCache<HashedItem, Optional<TestableItem>>) cacheField.get(null);
    }

    @AfterEach
    void cleanup() throws Exception {
        try {
            registered.forEach(Items::removeCustomItem);
            if (cache != null) cache.invalidateAll();
        } finally {
            if (bridgeField != null) bridgeField.set(null, previousBridge);
        }
    }

    private NamespacedKey register(Stack expected, boolean marked) {
        NamespacedKey key = new NamespacedKey("presence_test", "item_" + expected.id);
        registered.add(key);
        register(key, expected, marked);
        return key;
    }

    private void register(NamespacedKey key, Stack expected, boolean marked) {
        Items.registerCustomItem(key, new TestableItem() {
            @Override public boolean matches(ItemStack item) { matches.incrementAndGet(); return item == expected; }
            @Override public boolean shouldMarkAsCustom() { return marked; }
            @Override public ItemStack getItem() {
                examples.incrementAndGet();
                throw new AssertionError("Boolean recognition must not construct an example or schedule validation");
            }
        });
    }

    @Test void registeringCustomItemInvalidatesPriorNegativeRecognition() {
        Stack stack = new Stack(true);
        assertFalse(Items.isCustomItem(stack));
        register(stack, true);
        assertTrue(Items.isCustomItem(stack));
    }

    @Test void removingCustomItemInvalidatesPriorPositiveRecognition() {
        Stack stack = new Stack(true);
        NamespacedKey key = register(stack, true);
        assertTrue(Items.isCustomItem(stack));
        Items.removeCustomItem(key);
        assertFalse(Items.isCustomItem(stack));
        assertNull(Items.getCustomItem(stack));
    }

    @Test void replacingCustomItemInvalidatesOldDefinition() {
        Stack stack = new Stack(true);
        NamespacedKey key = register(stack, true);
        assertTrue(Items.isCustomItem(stack));
        register(key, stack, false);
        assertFalse(Items.isCustomItem(stack));
        assertNull(Items.getCustomItem(stack));
        register(key, stack, true);
        assertTrue(Items.isCustomItem(stack));
    }

    @Test void nullDoesNotAccessBackend() {
        assertFalse(Items.isCustomItem(null));
        assertNull(Items.getCustomItem(null));
        assertEquals(0, wraps.get());
    }

    @Test void matchingTestableItemDoesNotCreateWrapperAndReusesRecognitionCache() {
        Stack stack = new Stack(true);
        register(stack, true);
        assertTrue(Items.isCustomItem(stack));
        assertTrue(Items.isCustomItem(stack));
        assertEquals(1, matches.get());
        assertEquals(0, examples.get());
        assertEquals(2, wraps.get());
    }

    @Test void nonmatchingItemRemainsFalseAndNegativeCacheIsReused() {
        register(new Stack(true), true);
        Stack ordinary = new Stack(true);
        assertFalse(Items.isCustomItem(ordinary));
        assertFalse(Items.isCustomItem(ordinary));
        assertNull(Items.getCustomItem(ordinary));
        assertEquals(1, matches.get());
        assertEquals(0, examples.get());
    }

    @Test void unmarkedRecipeCannotBecomeCustom() {
        Stack stack = new Stack(true);
        register(stack, false);
        assertFalse(Items.isCustomItem(stack));
        assertEquals(0, matches.get());
        assertEquals(0, examples.get());
    }

    @Test void itemWithoutMetadataDoesNotEvaluateRegisteredPredicates() {
        Stack stack = new Stack(false);
        register(stack, true);
        assertFalse(Items.isCustomItem(stack));
        assertEquals(0, matches.get());
    }

    @Test void explicitCacheInvalidationReevaluatesSameRecognition() {
        Stack stack = new Stack(true);
        register(stack, true);
        assertTrue(Items.isCustomItem(stack));
        cache.invalidateAll();
        assertTrue(Items.isCustomItem(stack));
        assertEquals(2, matches.get());
        assertEquals(0, examples.get());
    }
}
