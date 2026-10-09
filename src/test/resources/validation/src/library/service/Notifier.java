package library.service;

import library.model.Member;

/**
 * Sends messages to members.
 */
public interface Notifier {

    void send(Member member, String message);
}
