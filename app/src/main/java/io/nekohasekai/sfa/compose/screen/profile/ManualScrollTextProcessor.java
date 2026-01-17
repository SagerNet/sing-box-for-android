package io.nekohasekai.sfa.compose.screen.profile;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import com.blacksquircle.ui.editorkit.widget.TextProcessor;

public class ManualScrollTextProcessor extends TextProcessor {

  private final int touchSlop;
  private boolean allowCursorAutoScroll = true;
  private float downX;
  private float downY;
  private boolean userDragging;
  private int downSelectionStart = -1;
  private int downSelectionEnd = -1;
  private boolean restoringSelection;

  public ManualScrollTextProcessor(Context context) {
    this(context, null);
  }

  public ManualScrollTextProcessor(Context context, AttributeSet attrs) {
    this(context, attrs, android.R.attr.autoCompleteTextViewStyle);
  }

  public ManualScrollTextProcessor(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
  }

  public void resumeAutoScroll() {
    allowCursorAutoScroll = true;
    userDragging = false;
  }

  @Override
  public boolean bringPointIntoView(int offset) {
    if (allowCursorAutoScroll) {
      return super.bringPointIntoView(offset);
    }
    return false;
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    int action = event.getActionMasked();
    switch (action) {
      case MotionEvent.ACTION_DOWN:
        downX = event.getX();
        downY = event.getY();
        userDragging = false;
        restoringSelection = false;
        downSelectionStart = getSelectionStart();
        downSelectionEnd = getSelectionEnd();
        break;
      case MotionEvent.ACTION_MOVE:
        if (!userDragging) {
          float dx = Math.abs(event.getX() - downX);
          float dy = Math.abs(event.getY() - downY);
          if (dx > touchSlop || dy > touchSlop) {
            userDragging = true;
            allowCursorAutoScroll = false;
          }
        }
        break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        break;
      default:
        break;
    }

    boolean handled = super.onTouchEvent(event);

    switch (action) {
      case MotionEvent.ACTION_MOVE:
        if (userDragging) {
          maybeRestoreSelection();
        }
        break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        if (userDragging) {
          maybeRestoreSelection();
        } else {
          resumeAutoScroll();
        }
        break;
      default:
        break;
    }

    return handled;
  }

  private void maybeRestoreSelection() {
    if (userDragging && !restoringSelection) {
      int selStart = getSelectionStart();
      int selEnd = getSelectionEnd();
      if (selStart != downSelectionStart || selEnd != downSelectionEnd) {
        restoringSelection = true;
        int targetEnd = downSelectionEnd >= 0 ? downSelectionEnd : downSelectionStart;
        setSelection(downSelectionStart, targetEnd);
      }
    }
  }

  @Override
  protected void onSelectionChanged(int selStart, int selEnd) {
    if (restoringSelection) {
      restoringSelection = false;
      super.onSelectionChanged(selStart, selEnd);
      return;
    }

    if (userDragging) {
      if (downSelectionStart >= 0
          && (selStart != downSelectionStart || selEnd != downSelectionEnd)) {
        restoringSelection = true;
        int targetEnd = downSelectionEnd >= 0 ? downSelectionEnd : downSelectionStart;
        setSelection(downSelectionStart, targetEnd);
        return;
      }
    }

    downSelectionStart = selStart;
    downSelectionEnd = selEnd;
    super.onSelectionChanged(selStart, selEnd);
  }
}
