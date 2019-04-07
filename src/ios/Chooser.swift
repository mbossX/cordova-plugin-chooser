import UIKit
import MobileCoreServices
import AVFoundation
import Foundation


@objc(Chooser)
class Chooser : CDVPlugin {
    var commandCallback: String?
    
    func callPicker (utis: [String]) {
       if !UIImagePickerController.isSourceTypeAvailable(.photoLibrary) {
            self.send("RESULT_CANCELED");
            return;
        }
        let imagePicker = UIImagePickerController()
        //设置代理
        imagePicker.delegate = self
        //指定图片控制器类型
        imagePicker.sourceType = .photoLibrary
        //只显示视频类型的文件
        imagePicker.mediaTypes = utis
        //不需要编辑
        imagePicker.allowsEditing = false
        //弹出控制器，显示界面
        self.viewController.present(imagePicker, animated: true, completion: nil)
    }
    
    func detectMimeType (_ url: URL) -> String {
        if let uti = UTTypeCreatePreferredIdentifierForTag(
            kUTTagClassFilenameExtension,
            url.pathExtension as CFString,
            nil
            )?.takeRetainedValue() {
            if let mimetype = UTTypeCopyPreferredTagWithClass(
                uti,
                kUTTagClassMIMEType
                )?.takeRetainedValue() as String? {
                return mimetype
            }
        }
        
        return "application/octet-stream"
    }
    
    func documentWasSelected (url: URL) {
        var error: NSError?
        
        NSFileCoordinator().coordinate(
            readingItemAt: url,
            options: [],
            error: &error
        ) { newURL in
            let maybeData = try? Data(contentsOf: newURL, options: [])
            
            guard let data = maybeData else {
                self.sendError("Failed to fetch data.")
                return
            }
            do {
                self.send("start")
                let mediaType = self.detectMimeType(newURL)
                var config = ["mediaType": mediaType];
                if mediaType.contains("video") {
                    let avAsset = AVAsset(url: newURL)
                    //生成视频截图
                    let generator = AVAssetImageGenerator(asset: avAsset)
                    generator.appliesPreferredTrackTransform = true
                    let time = CMTimeMakeWithSeconds(0.0,600)
                    var actualTime: CMTime = CMTimeMake(0,0)
                    let image: CGImage = try! generator.copyCGImage(at: time, actualTime: &actualTime)
                    config["w"] = String(image.width)
                    config["h"] = String(image.height)
                    config["thumbnail"] = self.getThumbnail(UIImage(cgImage: image))
                    config["duration"] = String(CMTimeGetSeconds(avAsset.duration))
                } else if let image = UIImage(data: data) {
                    config["w"] = String(Int(image.size.width))
                    config["h"] = String(Int(image.size.height))
                    config["thumbnail"] = self.getThumbnail(image)
                } else {
                    self.sendError("no result")
                }
                if let message = try String(
                    data: JSONSerialization.data(
                        withJSONObject: config,
                        options: []
                    ),
                    encoding: String.Encoding.utf8
                    ) {
                    self.send(message)
                }
                else {
                    self.sendError("Serializing result failed.")
                }
                
                let bytes = [UInt8](data)
                var len = bytes.count
                repeat {
                    var count = 1024 * 512
                    if len < count {
                        count = len
                    }
                    let start = bytes.count - len
                    self.send(Data(bytes: bytes[start...start + count - 1]))
                    len = len - count
                } while(len > 0)
                
                newURL.stopAccessingSecurityScopedResource()
                self.send("end")
            }
            catch let error {
                self.sendError(error.localizedDescription)
            }
        }
        
        if let error = error {
            self.sendError(error.localizedDescription)
        }
        
        url.stopAccessingSecurityScopedResource()
    }
    
    @objc(getFile:)
    func getFile (command: CDVInvokedUrlCommand) {
        self.commandCallback = command.callbackId
        
        let accept = command.arguments.first as! String
        let mimeTypes = accept.components(separatedBy: ",")
        
        let utis = mimeTypes.map { (mimeType: String) -> String in
            switch mimeType {
            case "audio/*":
                return kUTTypeAudio as String
            case "font/*":
                return "public.font"
            case "image/*":
                return kUTTypeImage as String
            case "text/*":
                return kUTTypeText as String
            case "video/*":
                return kUTTypeVideo as String
            default:
                break
            }
            
            if mimeType.range(of: "*") == nil {
                let utiUnmanaged = UTTypeCreatePreferredIdentifierForTag(
                    kUTTagClassMIMEType,
                    mimeType as CFString,
                    nil
                )
                
                if let uti = (utiUnmanaged?.takeRetainedValue() as String?) {
                    if !uti.hasPrefix("dyn.") {
                        return uti
                    }
                }
            }
            
            return kUTTypeData as String
        }
        
        self.callPicker(utis: utis)
    }
    
    func send (_ message: String, _ status: CDVCommandStatus = CDVCommandStatus_OK, _ final: Bool = false) {
        if let callbackId = self.commandCallback {
            if(final) {
                self.commandCallback = nil
            }
            
            let pluginResult = CDVPluginResult(
                status: status,
                messageAs: message
            )
            pluginResult?.setKeepCallbackAs(true)
            
            self.commandDelegate!.send(
                pluginResult,
                callbackId: callbackId
            )
        }
    }
    
    func send (_ buffer: Data!) {
        if let callbackId = self.commandCallback {
            let pluginResult = CDVPluginResult(
                status: CDVCommandStatus_OK,
                messageAsArrayBuffer: buffer
            )
            pluginResult?.setKeepCallbackAs(true)
            
            self.commandDelegate!.send(
                pluginResult,
                callbackId: callbackId
            )
        }
    }
    
    func sendError (_ message: String) {
        self.send(message, CDVCommandStatus_ERROR, true)
    }
    
    func getThumbnail(_ image: UIImage) -> String {
        var res: String?;
        UIGraphicsBeginImageContext(CGSize(width: 128, height: 128))
        image.draw(in: CGRect(x: 0, y: 0, width: 128, height: 128))
        if let image_ = UIGraphicsGetImageFromCurrentImageContext() {
            let data = UIImageJPEGRepresentation(image_, 0.9)
            res = data?.base64EncodedString(options: .lineLength64Characters)
        }
        UIGraphicsEndImageContext()
        return res ?? "";
    }
}

extension Chooser : UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        self.send("RESULT_CANCELED")
    }
    func imagePickerController(_ picker: UIImagePickerController,
                               didFinishPickingMediaWithInfo info: [String : Any]) {
        var url: URL?
        if let type = info[UIImagePickerControllerMediaType] as? String {
            if type == "public.image" {
                if #available(iOS 11.0, *) {
                    url = info[UIImagePickerControllerImageURL] as? URL
                } else {
                    url = info[UIImagePickerControllerReferenceURL] as? URL
                }
            } else {
                url = info[UIImagePickerControllerMediaType] as? URL
            }
        }
        if let url_ = url {
            self.documentWasSelected(url: url_)
            self.viewController.dismiss(animated: true, completion: {})
        }
    }
}
