package library.service;

import library.model.Member;

/**
 * A notifier that writes on the standard output.
 */
public class ConsoleNotifier implements Notifier {

    private int sent;

    @Override
    public void send(Member member, String message) {
        sent++;
        System.out.println("[" + member.getName() + "] " + message);
    }

    public int getSent() {
        return sent;
    }
}
