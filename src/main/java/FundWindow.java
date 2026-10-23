import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;
import utils.LogUtil;

public class FundWindow implements ToolWindowFactory {
    @Override
    public void init(@NotNull ToolWindow toolWindow) {
        toolWindow.setTitle(StockWindow.NAME);
        toolWindow.setStripeTitle(StockWindow.NAME);
    }

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        LogUtil.setProject(project);
        StockWindow window = new StockWindow();
        Content content = ContentFactory.getInstance().createContent(window.getmPanel(), StockWindow.NAME, false);
        content.setDisposer(new Disposable() {
            @Override
            public void dispose() {
                window.dispose();
            }
        });
        toolWindow.getContentManager().addContent(content);
        toolWindow.getContentManager().setSelectedContent(content);
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return true;
    }

    @Override
    public boolean isDoNotActivateOnStart() {
        return true;
    }
}
