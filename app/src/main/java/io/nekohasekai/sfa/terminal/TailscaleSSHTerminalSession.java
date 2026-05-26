package io.nekohasekai.sfa.terminal;

import android.os.Handler;
import android.os.Looper;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import io.nekohasekai.libbox.TailscaleSSHSession;
import java.util.Arrays;

public class TailscaleSSHTerminalSession extends TerminalSession {

  public enum Phase {
    CONNECTING,
    RUNNING,
    FINISHED
  }

  public interface PhaseCallback {
    void onPhaseChanged(Phase phase);

    void onAuthBanner(String message);
  }

  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private TailscaleSSHSession sshSession;
  private PhaseCallback phaseCallback;
  private volatile Phase phase = Phase.CONNECTING;
  private int exitCode;
  private String exitSignal;
  private String exitErrorMessage;
  private int lastColumns;
  private int lastRows;
  private int lastCellWidthPixels;
  private int lastCellHeightPixels;

  public TailscaleSSHTerminalSession(TerminalSessionClient client) {
    super("", "", new String[0], new String[0], null, client);
  }

  public void setPhaseCallback(PhaseCallback callback) {
    this.phaseCallback = callback;
  }

  public void setSshSession(TailscaleSSHSession session) {
    this.sshSession = session;
    if (mEmulator != null && lastColumns > 0 && lastRows > 0) {
      try {
        session.sendResize(lastColumns, lastRows, lastCellWidthPixels, lastCellHeightPixels);
      } catch (Exception ignored) {
      }
    }
  }

  public Phase getPhase() {
    return phase;
  }

  public int getSSHExitCode() {
    return exitCode;
  }

  public String getSSHExitSignal() {
    return exitSignal;
  }

  public String getSSHExitErrorMessage() {
    return exitErrorMessage;
  }

  @Override
  public void initializeEmulator(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
    mEmulator =
        new TerminalEmulator(this, columns, rows, cellWidthPixels, cellHeightPixels, null, mClient);
  }

  @Override
  public void updateSize(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
    lastColumns = columns;
    lastRows = rows;
    lastCellWidthPixels = cellWidthPixels;
    lastCellHeightPixels = cellHeightPixels;
    if (mEmulator == null) {
      initializeEmulator(columns, rows, cellWidthPixels, cellHeightPixels);
    } else {
      mEmulator.resize(columns, rows, cellWidthPixels, cellHeightPixels);
    }
    if (sshSession != null) {
      try {
        sshSession.sendResize(columns, rows, cellWidthPixels, cellHeightPixels);
      } catch (Exception ignored) {
      }
    }
  }

  @Override
  public void write(byte[] data, int offset, int count) {
    if (sshSession == null) return;
    byte[] sanitized = sanitizeInput(data, offset, count);
    try {
      sshSession.sendInput(sanitized);
    } catch (Exception ignored) {
    }
  }

  @Override
  public synchronized boolean isRunning() {
    return phase == Phase.RUNNING || phase == Phase.CONNECTING;
  }

  @Override
  public void finishIfRunning() {
    close();
  }

  public void feedOutput(byte[] data) {
    mainHandler.post(
        () -> {
          if (mEmulator != null) {
            mEmulator.append(data, data.length);
            notifyScreenUpdate();
          }
        });
  }

  public void onReady() {
    phase = Phase.RUNNING;
    mainHandler.post(
        () -> {
          if (phaseCallback != null) {
            phaseCallback.onPhaseChanged(Phase.RUNNING);
          }
        });
  }

  public void onAuthBanner(String message) {
    mainHandler.post(
        () -> {
          if (phaseCallback != null) {
            phaseCallback.onAuthBanner(message);
          }
        });
  }

  public void onExit(int exitCode, String signal, String errorMessage) {
    this.exitCode = exitCode;
    this.exitSignal = signal;
    this.exitErrorMessage = errorMessage;
    phase = Phase.FINISHED;
    mainHandler.post(
        () -> {
          if (mEmulator != null) {
            StringBuilder exitText = new StringBuilder("\r\n[Session ended");
            if (errorMessage != null && !errorMessage.isEmpty()) {
              exitText.append(": ").append(errorMessage);
            } else if (exitCode != 0) {
              exitText.append(" (exit ").append(exitCode).append(")");
            }
            if (signal != null && !signal.isEmpty()) {
              exitText.append(" (signal ").append(signal).append(")");
            }
            exitText.append(" - press any key]");
            byte[] bytes = exitText.toString().getBytes();
            mEmulator.append(bytes, bytes.length);
            notifyScreenUpdate();
          }
          if (phaseCallback != null) {
            phaseCallback.onPhaseChanged(Phase.FINISHED);
          }
          mClient.onSessionFinished(TailscaleSSHTerminalSession.this);
        });
  }

  public void onError(String message) {
    onExit(-1, null, message);
  }

  public void close() {
    if (sshSession != null) {
      try {
        sshSession.close();
      } catch (Exception ignored) {
      }
      sshSession = null;
    }
  }

  private static byte[] sanitizeInput(byte[] data, int offset, int count) {
    byte[] result = new byte[count];
    int writePos = 0;
    for (int i = offset; i < offset + count; i++) {
      byte b = data[i];
      if (b == 0x1b && i + 4 < offset + count) {
        // Strip bracketed paste markers: ESC[200~ and ESC[201~
        if (data[i + 1] == '['
            && data[i + 2] == '2'
            && data[i + 3] == '0'
            && (data[i + 4] == '0' || data[i + 4] == '1')
            && i + 5 < offset + count
            && data[i + 5] == '~') {
          i += 5;
          continue;
        }
      }
      // Convert LF to CR
      if (b == 0x0A) {
        result[writePos++] = 0x0D;
      } else {
        result[writePos++] = b;
      }
    }
    if (writePos == count) {
      return result;
    }
    return Arrays.copyOf(result, writePos);
  }
}
