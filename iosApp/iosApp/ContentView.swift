import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let controller = MainViewControllerKt.MainViewController()
        controller.view.backgroundColor = UIColor(
            red: 11.0 / 255.0,
            green: 12.0 / 255.0,
            blue: 14.0 / 255.0,
            alpha: 1.0
        )
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ZStack {
            Color(red: 11.0 / 255.0, green: 12.0 / 255.0, blue: 14.0 / 255.0)
                .ignoresSafeArea()
            ComposeView()
                .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
    }
}
