import customtkinter as ctk
from ui.dashboard import DashboardFrame
from ui.attendance import AttendanceFrame
from ui.students import StudentsFrame
from ui.courses import CoursesFrame
from ui.reports import ReportsFrame
from ui.settings import SettingsFrame

ctk.set_appearance_mode("light")
ctk.set_default_color_theme("blue")

NAV_ITEMS = [
    ("Dashboard",    "📊", DashboardFrame),
    ("Presenze",     "📅", AttendanceFrame),
    ("Allievi",      "👤", StudentsFrame),
    ("Corsi",        "📚", CoursesFrame),
    ("Report PDF",   "📄", ReportsFrame),
    ("Impostazioni", "⚙️", SettingsFrame),
]

SIDEBAR_BG   = "#2c3e50"
SIDEBAR_HOV  = "#34495e"
SIDEBAR_SEL  = "#1a252f"
SIDEBAR_TEXT = "#ecf0f1"
MAIN_BG      = "#f5f6fa"


class App(ctk.CTk):
    def __init__(self):
        super().__init__()
        self.title("Conteggio Ore Allievi")
        self.geometry("1100x680")
        self.minsize(900, 600)
        self.configure(fg_color=MAIN_BG)

        self._frames = {}
        self._nav_buttons = {}
        self._build_layout()
        self._show_frame("Dashboard")

    def _build_layout(self):
        # Sidebar
        self.sidebar = ctk.CTkFrame(self, width=190, fg_color=SIDEBAR_BG, corner_radius=0)
        self.sidebar.pack(side="left", fill="y")
        self.sidebar.pack_propagate(False)

        logo = ctk.CTkLabel(
            self.sidebar, text="Conteggio\nOre Allievi",
            font=ctk.CTkFont(size=15, weight="bold"),
            text_color=SIDEBAR_TEXT
        )
        logo.pack(pady=(24, 20), padx=10)

        sep = ctk.CTkFrame(self.sidebar, height=1, fg_color=SIDEBAR_HOV)
        sep.pack(fill="x", padx=12, pady=(0, 16))

        for label, icon, _ in NAV_ITEMS:
            btn = ctk.CTkButton(
                self.sidebar,
                text=f"{icon}  {label}",
                anchor="w",
                height=42,
                font=ctk.CTkFont(size=13),
                fg_color="transparent",
                hover_color=SIDEBAR_HOV,
                text_color=SIDEBAR_TEXT,
                corner_radius=6,
                command=lambda l=label: self._show_frame(l)
            )
            btn.pack(fill="x", padx=10, pady=2)
            self._nav_buttons[label] = btn

        # Area contenuto principale
        self.content = ctk.CTkFrame(self, fg_color=MAIN_BG, corner_radius=0)
        self.content.pack(side="left", fill="both", expand=True)

        for label, _, FrameClass in NAV_ITEMS:
            frame = FrameClass(self.content, self)
            frame.place(relx=0, rely=0, relwidth=1, relheight=1)
            self._frames[label] = frame

    def _show_frame(self, name):
        for label, btn in self._nav_buttons.items():
            btn.configure(fg_color=SIDEBAR_SEL if label == name else "transparent")

        frame = self._frames[name]
        frame.tkraise()
        if hasattr(frame, "on_show"):
            frame.on_show()

    def refresh_frame(self, name):
        frame = self._frames[name]
        if hasattr(frame, "on_show"):
            frame.on_show()
