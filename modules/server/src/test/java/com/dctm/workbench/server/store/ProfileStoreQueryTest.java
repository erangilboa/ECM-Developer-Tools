package com.dctm.workbench.server.store;

import com.dctm.workbench.core.Product;
import com.dctm.workbench.core.SavedQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileStoreQueryTest {

    @TempDir
    Path temp;

    @Test
    void queryHistoryAndDelete() {
        ProfileStore store = new ProfileStore(temp.toString());
        store.appendQueryHistory("SELECT * FROM dm_document", Product.DOCUMENTUM);
        store.appendQueryHistory("SELECT object_name FROM dm_folder", Product.DOCUMENTUM);
        assertThat(store.queryHistory(Product.DOCUMENTUM)).hasSize(2);

        com.dctm.workbench.core.ExecutionHistoryEntry exec = new com.dctm.workbench.core.ExecutionHistoryEntry();
        exec.setKind("DQL");
        exec.setProduct(Product.DOCUMENTUM);
        exec.setSummary("test");
        exec.setRequestText("SELECT 1");
        exec.setSuccess(true);
        store.appendExecutionHistory(exec);
        assertThat(store.executionHistory(Product.DOCUMENTUM)).hasSize(1);

        SavedQuery q = new SavedQuery();
        q.setName("test");
        q.setText("SELECT 1");
        q.setProduct(Product.DOCUMENTUM);
        SavedQuery saved = store.saveQuery(q);
        assertThat(store.queries()).hasSize(1);
        store.deleteQuery(saved.getId());
        assertThat(store.queries()).isEmpty();
    }
}
