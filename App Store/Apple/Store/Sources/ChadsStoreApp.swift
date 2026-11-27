import SwiftUI
import UIKit

@main
struct ChadsStoreApp: App {
    var body: some Scene {
        WindowGroup {
            StoreHome()
                .preferredColorScheme(.dark)
        }
    }
}

struct StoreHome: View {
    @StateObject private var catalog = StoreCatalog()
    @State private var platform: StorePlatform = .ios
    @State private var query = ""
    @State private var copiedSource = false

    private let background = Color(red: 0.063, green: 0.078, blue: 0.106)
    private var visible: [StoreEntry] {
        catalog.entries.filter { entry in
            entry.record.platform == platform.rawValue &&
            (query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
             (entry.record.name + " " + entry.record.category + " " + entry.record.description)
                .localizedCaseInsensitiveContains(query.trimmingCharacters(in: .whitespacesAndNewlines)))
        }.sorted { ($0.record.category + $0.record.name) < ($1.record.category + $1.record.name) }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Text("CHAD'S APP STORE")
                    .font(.caption.bold())
                    .tracking(2)
                    .foregroundStyle(.secondary)
                Text("Your apps. Your platform.")
                    .font(.system(size: 38, weight: .heavy))
                    .tracking(-1.6)
                HStack(spacing: 10) {
                    tab(.android)
                    tab(.ios)
                }
                HStack {
                    Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                    TextField("Search applications", text: $query)
                        .autocorrectionDisabled()
                }
                .padding(13)
                .background(.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 14))
                Button {
                    Task { await catalog.refresh() }
                } label: {
                    HStack {
                        Spacer()
                        if catalog.loading { ProgressView().padding(.trailing, 4) }
                        Text(catalog.loading ? "Checking releases…" : "Refresh / Check for updates")
                        Spacer()
                    }
                    .font(.subheadline.bold())
                    .padding(13)
                    .background(.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 14))
                }
                .buttonStyle(.plain)
                .disabled(catalog.loading)

                Text(catalog.status)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .accessibilityLabel(catalog.status)

                Text(platform == .ios ? "APPLE / IPA" : "ANDROID / APK")
                    .font(.caption.bold())
                    .tracking(1.5)
                    .foregroundStyle(platform.accent)

                if visible.isEmpty && !catalog.loading {
                    Text(query.isEmpty ? "No apps available yet." : "No matching applications.")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
                ForEach(Array(Set(visible.map { $0.record.category })).sorted(), id: \.self) { category in
                    Text(category).font(.headline).foregroundStyle(.secondary)
                    ForEach(visible.filter { $0.record.category == category }) { entry in
                        appCard(entry)
                    }
                }
                installationGuide
                Link(destination: StoreCatalog.releasesURL) {
                    Label("GitHub release history", systemImage: "arrow.up.right.square")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
            .padding(22)
        }
        .background(background.ignoresSafeArea())
        .task { await catalog.refresh() }
        .refreshable { await catalog.refresh() }
    }

    private func tab(_ value: StorePlatform) -> some View {
        Button {
            platform = value
        } label: {
            VStack(alignment: .leading, spacing: 6) {
                Image(systemName: value == .ios ? "iphone" : "square.grid.2x2")
                    .font(.title2)
                Text(value == .ios ? "Apple" : "Android").font(.title3.bold())
                Text(value == .ios ? "IPA apps" : "APK apps").font(.caption)
            }
            .foregroundStyle(value.accent)
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(value.accent.opacity(platform == value ? 0.23 : 0.09),
                        in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16)
                .stroke(value.accent.opacity(platform == value ? 0.8 : 0.25)))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(platform == value ? [.isSelected] : [])
    }

    private func appCard(_ entry: StoreEntry) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: "square.grid.2x2")
                    .font(.title2)
                    .foregroundStyle(platform.accent)
                    .frame(width: 46, height: 46)
                    .background(platform.accent.opacity(0.12), in: RoundedRectangle(cornerRadius: 12))
                VStack(alignment: .leading, spacing: 4) {
                    Text(entry.record.name).font(.headline)
                    Text(entry.record.description).font(.subheadline).foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
            }
            if let build = entry.build {
                Text("Version " + build.version + " · " + ByteCountFormatter.string(fromByteCount: build.size, countStyle: .file))
                    .font(.caption)
                    .foregroundStyle(platform.accent)
                Link(destination: build.url) {
                    HStack {
                        Spacer()
                        Label(platform == .ios ? "Download IPA" : "Download APK", systemImage: "arrow.down.circle")
                        Spacer()
                    }
                    .font(.subheadline.bold())
                    .padding(12)
                    .foregroundStyle(Color.black)
                    .background(platform.accent, in: RoundedRectangle(cornerRadius: 12))
                }
                if platform == .ios {
                    ShareLink(item: build.url) {
                        Label("Share IPA download link", systemImage: "square.and.arrow.up")
                            .font(.caption)
                    }
                    .foregroundStyle(platform.accent)
                }
            } else {
                Text("Not released").font(.caption).foregroundStyle(.secondary)
            }
        }
        .padding(15)
        .background(.white.opacity(0.055), in: RoundedRectangle(cornerRadius: 17))
    }

    private var installationGuide: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(platform == .ios ? "iPhone installation" : "Android installation")
                .font(.headline)
            if platform == .ios {
                Text("IPA files must be signed and installed using a compatible method. Downloading an IPA does not install it. Use your own Apple account and follow the sideloading tool's instructions.")
                    .font(.subheadline).foregroundStyle(.secondary)
                Link("SideStore setup", destination: URL(string: "https://sidestore.io/")!)
                Link("AltStore Classic setup", destination: URL(string: "https://altstore.io/")!)
                Button {
                    UIPasteboard.general.string = StoreCatalog.sourceURL.absoluteString
                    copiedSource = true
                } label: {
                    Label(copiedSource ? "Source URL copied" : "Copy iPhone source URL",
                          systemImage: copiedSource ? "checkmark.circle" : "doc.on.doc")
                }
                ShareLink(item: StoreCatalog.sourceURL) {
                    Label("Share iPhone source", systemImage: "square.and.arrow.up")
                }
            } else {
                Text("APK files install on Android devices, not iPhones. Download and open the APK on Android, then approve the system installation prompt.")
                    .font(.subheadline).foregroundStyle(.secondary)
            }
        }
        .font(.subheadline)
        .tint(platform.accent)
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.white.opacity(0.045), in: RoundedRectangle(cornerRadius: 16))
    }
}

enum StorePlatform: String {
    case android, ios

    var accent: Color {
        switch self {
        case .android: return Color(red: 0.396, green: 0.894, blue: 0.631)
        case .ios: return Color(red: 0.624, green: 0.769, blue: 1)
        }
    }
}
