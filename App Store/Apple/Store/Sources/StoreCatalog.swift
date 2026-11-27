import Foundation
import Combine

struct CatalogRecord: Decodable {
    let id: String
    let platform: String
    let name: String
    let description: String
    let category: String
    let releasePrefix: String
    let asset: String
    let packageId: String?
}

struct StoreEntry: Identifiable {
    let record: CatalogRecord
    let build: PublishedBuild?
    var id: String { record.id }
}

struct PublishedBuild {
    let version: String
    let number: Int
    let url: URL
    let size: Int64
}

private struct Release: Decodable {
    let tag_name: String
    let draft: Bool
    let assets: [Asset]
}

private struct Asset: Decodable {
    let name: String
    let browser_download_url: URL
    let size: Int64
}

@MainActor
final class StoreCatalog: ObservableObject {
    private static let repo = "Chad-did-this/Chads-app-store"
    static let releasesURL = URL(string: "https://github.com/" + repo + "/releases")!
    static let sourceURL = URL(string: "https://github.com/" + repo + "/releases/download/ios-source/source.json")!
    private static let registryURL = URL(string: "https://raw.githubusercontent.com/" + repo + "/main/docs/catalog.json")!
    private static let apiURL = URL(string: "https://api.github.com/repos/" + repo + "/releases?per_page=100")!

    @Published private(set) var entries: [StoreEntry] = []
    @Published private(set) var status = "Ready to check releases."
    @Published private(set) var loading = false

    func refresh() async {
        guard !loading else { return }
        loading = true
        status = "Checking GitHub releases…"
        defer { loading = false }
        do {
            async let records = Self.fetch([CatalogRecord].self, from: Self.registryURL)
            async let releases = Self.fetch([Release].self, from: Self.apiURL)
            let (registry, published) = try await (records, releases)
            var seen = Set<String>()
            entries = registry.filter {
                ["android", "ios"].contains($0.platform) && seen.insert($0.id).inserted
            }.map { record in
                StoreEntry(record: record, build: Self.latest(record, from: published))
            }
            status = "Catalog updated."
        } catch {
            if entries.isEmpty {
                status = "Unable to load the catalog. Refresh to try again."
            } else {
                status = "Refresh failed. Showing the last successful catalog."
            }
        }
    }

    private static func fetch<T: Decodable>(_ type: T.Type, from url: URL) async throws -> T {
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 30)
        request.setValue("ChadsAppStore/1.0", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse,
              http.statusCode == 200,
              http.url?.scheme == "https",
              http.url?.host == url.host,
              data.count <= 4 * 1024 * 1024 else {
            throw URLError(.badServerResponse)
        }
        return try JSONDecoder().decode(type, from: data)
    }

    private static func latest(_ record: CatalogRecord, from releases: [Release]) -> PublishedBuild? {
        var best: PublishedBuild?
        let suffix = record.platform == "ios" ? ".ipa" : ".apk"
        guard record.asset.hasSuffix(suffix) else { return nil }
        for release in releases where !release.draft && release.tag_name.hasPrefix(record.releasePrefix) {
            let version = String(release.tag_name.dropFirst(record.releasePrefix.count))
            guard let number = version.split(separator: ".").last.flatMap({ Int($0) }),
                  number > (best?.number ?? 0),
                  let asset = release.assets.first(where: { $0.name == record.asset }),
                  asset.size > 0,
                  asset.browser_download_url.scheme == "https",
                  asset.browser_download_url.host == "github.com",
                  asset.browser_download_url.path.hasPrefix("/" + repo + "/releases/download/") else { continue }
            best = PublishedBuild(version: version, number: number,
                                  url: asset.browser_download_url, size: asset.size)
        }
        return best
    }
}
