// NfcReader.swift — built-in CoreNFC NDEF tag reader with custom system sheet copy.

import CoreNFC
import Foundation

enum NfcReaderError: LocalizedError {
    case unsupported
    case emptyTag
    case sessionFailed(String)

    var errorDescription: String? {
        switch self {
        case .unsupported: return "NFC is not available on this device."
        case .emptyTag: return "This NFC tag does not contain a credential or verification link."
        case .sessionFailed(let msg): return msg
        }
    }
}

final class NfcReader: NSObject, NFCNDEFReaderSessionDelegate {

    static var isSupported: Bool {
        NFCNDEFReaderSession.readingAvailable
    }

    private var continuation: CheckedContinuation<String, Error>?
    private var session: NFCNDEFReaderSession?

    func readTag() async throws -> String {
        guard Self.isSupported else { throw NfcReaderError.unsupported }
        return try await withCheckedThrowingContinuation { cont in
            self.continuation = cont
            let s = NFCNDEFReaderSession(delegate: self, queue: nil, invalidateAfterFirstRead: true)
            s.alertMessage = "Hold the top of your iPhone near the NFC reader."
            self.session = s
            s.begin()
        }
    }

    func readerSession(_ session: NFCNDEFReaderSession, didInvalidateWithError error: Error) {
        let nsError = error as NSError
        // Ignore normal first-read completion or user cancel silently where appropriate.
        if nsError.code == NFCReaderError.readerSessionInvalidationErrorFirstNDEFTagRead.rawValue {
            return
        }
        if let cont = continuation {
            continuation = nil
            cont.resume(throwing: NfcReaderError.sessionFailed(error.localizedDescription))
        }
    }

    func readerSession(_ session: NFCNDEFReaderSession, didDetectNDEFs messages: [NFCNDEFMessage]) {
        for message in messages {
            for record in message.records {
                if let url = record.wellKnownTypeURIPayload()?.absoluteString, !url.isEmpty {
                    finish(with: .success(url))
                    return
                }
                let (text, _) = record.wellKnownTypeTextPayload()
                if let text, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    finish(with: .success(text.trimmingCharacters(in: .whitespacesAndNewlines)))
                    return
                }
            }
        }
        finish(with: .failure(NfcReaderError.emptyTag))
    }

    private func finish(with result: Result<String, Error>) {
        guard let cont = continuation else { return }
        continuation = nil
        session = nil
        cont.resume(with: result)
    }
}
