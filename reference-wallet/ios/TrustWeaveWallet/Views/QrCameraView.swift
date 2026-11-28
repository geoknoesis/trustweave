// QrCameraView.swift — built-in AVFoundation live QR viewfinder with torch and corner reticle.

import AVFoundation
import SwiftUI
import UIKit

struct QrCameraView: UIViewControllerRepresentable {
    var torchEnabled: Bool
    var isActive: Bool
    var onQrDecoded: (String) -> Void

    func makeUIViewController(context: Context) -> QrScannerViewController {
        let vc = QrScannerViewController()
        vc.onQrDecoded = onQrDecoded
        return vc
    }

    func updateUIViewController(_ uiViewController: QrScannerViewController, context: Context) {
        uiViewController.onQrDecoded = onQrDecoded
        uiViewController.setRunning(isActive)
        uiViewController.setTorch(torchEnabled)
    }
}

final class QrScannerViewController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    var onQrDecoded: ((String) -> Void)?

    private let session = AVCaptureSession()
    private let sessionQueue = DispatchQueue(label: "org.trustweave.wallet.camera")
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var configured = false
    private var lastDecodeAt: Date = .distantPast

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 0.043, green: 0.067, blue: 0.125, alpha: 1.0)
        configureIfAuthorized()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
    }

    func setRunning(_ active: Bool) {
        sessionQueue.async { [weak self] in
            guard let self, self.configured else { return }
            if active && !self.session.isRunning {
                self.session.startRunning()
            } else if !active && self.session.isRunning {
                self.session.stopRunning()
            }
        }
    }

    func setTorch(_ enabled: Bool) {
        sessionQueue.async {
            guard let device = AVCaptureDevice.default(for: .video), device.hasTorch else { return }
            do {
                try device.lockForConfiguration()
                device.torchMode = enabled ? .on : .off
                device.unlockForConfiguration()
            } catch {
                // ignore torch errors on unsupported hardware
            }
        }
    }

    private func configureIfAuthorized() {
        guard AVCaptureDevice.authorizationStatus(for: .video) == .authorized else { return }
        sessionQueue.async { [weak self] in
            guard let self, !self.configured else { return }
            guard let device = AVCaptureDevice.default(for: .video),
                  let input = try? AVCaptureDeviceInput(device: device),
                  self.session.canAddInput(input) else {
                return
            }
            self.session.beginConfiguration()
            self.session.addInput(input)
            let output = AVCaptureMetadataOutput()
            if self.session.canAddOutput(output) {
                self.session.addOutput(output)
                output.setMetadataObjectsDelegate(self, queue: DispatchQueue.main)
                output.metadataObjectTypes = [.qr]
            }
            self.session.commitConfiguration()
            self.configured = true

            DispatchQueue.main.async {
                let layer = AVCaptureVideoPreviewLayer(session: self.session)
                layer.videoGravity = .resizeAspectFill
                layer.frame = self.view.bounds
                self.view.layer.insertSublayer(layer, at: 0)
                self.previewLayer = layer
            }
            self.session.startRunning()
        }
    }

    func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput metadataObjects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        guard let obj = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let value = obj.stringValue, !value.isEmpty else {
            return
        }
        let now = Date()
        guard now.timeIntervalSince(lastDecodeAt) > 1.5 else { return }
        lastDecodeAt = now
        onQrDecoded?(value)
    }
}
