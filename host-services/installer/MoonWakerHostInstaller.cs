using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace MoonWaker.HostInstaller
{
    internal static class Program
    {
        [STAThread]
        private static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new InstallerForm());
        }
    }

    internal sealed class InstallerForm : Form
    {
        private readonly Color background = Color.FromArgb(17, 20, 28);
        private readonly Color panelColor = Color.FromArgb(28, 33, 45);
        private readonly Color accent = Color.FromArgb(116, 100, 255);
        private readonly Panel content = new Panel();
        private readonly Button back = new Button();
        private readonly Button next = new Button();
        private readonly Label step = new Label();
        private int page;

        private readonly TextBox profileId = new TextBox();
        private readonly TextBox profileName = new TextBox();
        private readonly CheckBox discord = new CheckBox();
        private readonly CheckBox vibepollo = new CheckBox();
        private readonly CheckBox playnite = new CheckBox();
        private readonly TextBox discordId = new TextBox();
        private readonly TextBox discordSecret = new TextBox();
        private readonly TextBox vibepolloUrl = new TextBox();
        private readonly TextBox vibepolloToken = new TextBox();
        private readonly CheckBox createVibepolloToken = new CheckBox();
        private readonly TextBox vibepolloAdmin = new TextBox();
        private readonly TextBox vibepolloPassword = new TextBox();
        private readonly TextBox playnitePath = new TextBox();
        private readonly RichTextBox log = new RichTextBox();
        private readonly ProgressBar progress = new ProgressBar();

        public InstallerForm()
        {
            Text = "MoonWaker Host Installer";
            AutoScaleMode = AutoScaleMode.Dpi;
            ClientSize = new Size(920, 770);
            MinimumSize = new Size(920, 770);
            FormBorderStyle = FormBorderStyle.FixedSingle;
            MaximizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = background;
            ForeColor = Color.White;
            Font = new Font("Segoe UI", 10F);

            Label title = MakeLabel("MOONWAKER", 25F, FontStyle.Bold);
            title.ForeColor = Color.White;
            title.SetBounds(38, 24, 520, 46);
            Controls.Add(title);

            step.Font = new Font("Segoe UI", 9F, FontStyle.Bold);
            step.ForeColor = Color.FromArgb(170, 176, 197);
            step.TextAlign = ContentAlignment.MiddleRight;
            step.SetBounds(690, 34, 190, 28);
            Controls.Add(step);

            content.SetBounds(38, 88, 844, 595);
            content.BackColor = panelColor;
            Controls.Add(content);

            StyleButton(back, false);
            back.Text = "Wstecz";
            back.SetBounds(598, 704, 130, 42);
            back.Click += delegate { if (page > 0) { page--; ShowPage(); } };
            Controls.Add(back);

            StyleButton(next, true);
            next.SetBounds(742, 704, 140, 42);
            next.Click += NextClicked;
            Controls.Add(next);

            profileId.Text = "default";
            profileName.Text = Environment.UserName;
            discord.Checked = true;
            vibepollo.Checked = true;
            playnite.Checked = true;
            vibepolloUrl.Text = "https://127.0.0.1:47990";
            createVibepolloToken.CheckedChanged += delegate
            {
                vibepolloToken.Enabled = !createVibepolloToken.Checked;
                vibepolloAdmin.Enabled = createVibepolloToken.Checked;
                vibepolloPassword.Enabled = createVibepolloToken.Checked;
            };
            playnitePath.Text = FindPlaynite();
            ShowPage();
        }

        private void ShowPage()
        {
            content.Controls.Clear();
            back.Enabled = page > 0 && page < 2;
            if (page == 0) ShowProfilePage();
            else if (page == 1) ShowComponentsPage();
            else ShowProgressPage();
        }

        private void ShowProfilePage()
        {
            step.Text = "KROK 1 Z 3";
            next.Text = "Dalej";
            AddHeading("Profil gracza", "Gateway zostanie zainstalowany raz, a Bridge'e dla tego konta Windows.");
            AddField("Identyfikator profilu", profileId, 125, "Litery, cyfry, kropka, myślnik lub podkreślenie.");
            AddField("Nazwa wyświetlana", profileName, 245, "Ta nazwa pojawi się w ustawieniach hosta MoonWaker.");
            Label note = MakeLabel("Instalator działa dla aktualnie zalogowanego konta: " + Environment.UserDomainName + "\\" + Environment.UserName, 9F, FontStyle.Regular);
            note.ForeColor = Color.FromArgb(170, 176, 197);
            note.SetBounds(34, 375, 770, 32);
            content.Controls.Add(note);
        }

        private void ShowComponentsPage()
        {
            step.Text = "KROK 2 Z 3";
            next.Text = "Zainstaluj";
            AddHeading("Integracje", "Dane aplikacji Discord podajesz tylko raz na cały komputer.");

            ConfigureCheckBox(discord, "Discord + VirtualHere", 30, 126);
            ConfigureTextBox(discordId, 240, 120, 250, false);
            ConfigureTextBox(discordSecret, 510, 120, 285, true);
            AddSmallLabel("Application ID / Client ID", 240, 100, 250);
            AddSmallLabel("OAuth2 Client Secret", 510, 100, 285);
            bool machineDiscordConfigured = HasMachineDiscordApplication();
            discordId.Enabled = !machineDiscordConfigured;
            discordSecret.Enabled = !machineDiscordConfigured;
            if (machineDiscordConfigured)
            {
                discordId.Clear();
                discordSecret.Clear();
                Label configured = MakeLabel("✓ Dane aplikacji zapisane na tym komputerze", 8.5F, FontStyle.Regular);
                configured.ForeColor = Color.FromArgb(145, 215, 175);
                configured.SetBounds(30, 162, 195, 45);
                content.Controls.Add(configured);
            }
            LinkLabel discordHelp = new LinkLabel();
            discordHelp.Text = "Utwórz aplikację w Discord Developer Portal i skopiuj pola z General Information oraz OAuth2.";
            discordHelp.LinkColor = Color.FromArgb(155, 145, 255);
            discordHelp.ActiveLinkColor = Color.White;
            discordHelp.BackColor = panelColor;
            discordHelp.SetBounds(240, 160, 555, 38);
            discordHelp.LinkClicked += delegate { Process.Start("https://discord.com/developers/applications"); };
            content.Controls.Add(discordHelp);
            Label discordScopes = MakeLabel("Scope’y: rpc, identify, guilds, rpc.voice.read, rpc.voice.write", 8.5F, FontStyle.Regular);
            discordScopes.ForeColor = Color.FromArgb(155, 161, 180);
            discordScopes.SetBounds(240, 198, 555, 22);
            content.Controls.Add(discordScopes);

            ConfigureCheckBox(vibepollo, "Vibepollo", 30, 256);
            ConfigureTextBox(vibepolloUrl, 240, 250, 250, false);
            ConfigureTextBox(vibepolloToken, 510, 250, 285, true);
            AddSmallLabel("Adres lokalnego API", 240, 230, 250);
            AddSmallLabel("Istniejący token (opcjonalnie)", 510, 230, 285);

            ConfigureCheckBox(createVibepolloToken, "Utwórz token automatycznie", 240, 292);
            ConfigureTextBox(vibepolloAdmin, 240, 340, 250, false);
            ConfigureTextBox(vibepolloPassword, 510, 340, 285, true);
            AddSmallLabel("Login administratora Vibepollo", 240, 320, 250);
            AddSmallLabel("Hasło — nie zostanie zapisane", 510, 320, 285);
            vibepolloAdmin.Enabled = createVibepolloToken.Checked;
            vibepolloPassword.Enabled = createVibepolloToken.Checked;
            vibepolloToken.Enabled = !createVibepolloToken.Checked;
            Button showScopes = new Button();
            StyleButton(showScopes, false);
            showScopes.Text = "Pokaż 19 wymaganych uprawnień";
            showScopes.SetBounds(240, 384, 290, 32);
            showScopes.Click += delegate
            {
                MessageBox.Show(this,
                    "GET\n/api/metadata\n/api/session/status\n/api/host/stats\n/api/host/info\n/api/rtsp/sessions\n/api/webrtc/sessions\n/api/history/sessions/active\n/api/history/sessions\n/api/apps\n/api/clients/list\n/api/rtss/status\n/api/lossless_scaling/status\n/api/logs\n/api/logs/export\n\nPOST\n/api/apps/launch\n/api/apps/close\n/api/clients/disconnect\n/api/restart\n/api/reset-display-device-persistence",
                    "Uprawnienia tokenu Vibepollo", MessageBoxButtons.OK, MessageBoxIcon.Information);
            };
            content.Controls.Add(showScopes);

            ConfigureCheckBox(playnite, "Playnite", 30, 470);
            ConfigureTextBox(playnitePath, 240, 464, 485, false);
            AddSmallLabel("Katalog Playnite", 240, 444, 485);
            Button browse = new Button();
            StyleButton(browse, false);
            browse.Text = "…";
            browse.SetBounds(740, 464, 55, 34);
            browse.Click += delegate
            {
                using (FolderBrowserDialog dialog = new FolderBrowserDialog())
                {
                    dialog.Description = "Wybierz katalog instalacyjny Playnite";
                    if (dialog.ShowDialog(this) == DialogResult.OK) playnitePath.Text = dialog.SelectedPath;
                }
            };
            content.Controls.Add(browse);

            Label security = MakeLabel("Tokeny są chronione przez DPAPI. Hasło administratora Vibepollo służy tylko do żądania i nie jest zapisywane.", 9F, FontStyle.Regular);
            security.ForeColor = Color.FromArgb(160, 200, 180);
            security.SetBounds(30, 535, 765, 45);
            content.Controls.Add(security);
        }

        private void ShowProgressPage()
        {
            step.Text = "KROK 3 Z 3";
            next.Text = "Zamknij";
            next.Enabled = false;
            AddHeading("Instalacja", "Konfiguruję Gateway, Bridge'e i profil MoonWaker.");
            progress.Style = ProgressBarStyle.Marquee;
            progress.MarqueeAnimationSpeed = 28;
            progress.SetBounds(30, 102, 784, 8);
            content.Controls.Add(progress);
            log.BackColor = Color.FromArgb(20, 24, 33);
            log.ForeColor = Color.FromArgb(205, 210, 225);
            log.BorderStyle = BorderStyle.None;
            log.ReadOnly = true;
            log.Font = new Font("Consolas", 9F);
            log.SetBounds(30, 135, 784, 400);
            content.Controls.Add(log);
        }

        private async void NextClicked(object sender, EventArgs e)
        {
            if (page == 0)
            {
                if (!Regex.IsMatch(profileId.Text.Trim(), "^[A-Za-z0-9._-]{1,64}$"))
                {
                    MessageBox.Show(this, "Nieprawidłowy identyfikator profilu.", "MoonWaker", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                    return;
                }
                if (String.IsNullOrWhiteSpace(profileName.Text)) profileName.Text = profileId.Text.Trim();
                page = 1;
                ShowPage();
                return;
            }
            if (page == 1)
            {
                string validation = ValidateComponents();
                if (validation != null)
                {
                    MessageBox.Show(this, validation, "MoonWaker", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                    return;
                }
                page = 2;
                ShowPage();
                await InstallAsync();
                return;
            }
            Close();
        }

        private string ValidateComponents()
        {
            string root = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "WakePlayHost", "profiles", profileId.Text.Trim());
            bool hasDiscord = File.Exists(Path.Combine(root, "discord", "discord_bridge_config.json"));
            bool hasVibepollo = File.Exists(Path.Combine(root, "vibepollo", "config.json"));
            bool hasMachineDiscord = File.Exists(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData),
                "MoonWakerHost", "discord-app.json"));
            if (discord.Checked && !hasDiscord && !hasMachineDiscord &&
                (String.IsNullOrWhiteSpace(discordId.Text) || String.IsNullOrWhiteSpace(discordSecret.Text)))
                return "Pierwsza instalacja na tym komputerze wymaga Client ID i Client Secret aplikacji Discord.";
            if (discord.Checked && !String.IsNullOrWhiteSpace(discordId.Text) && !Regex.IsMatch(discordId.Text.Trim(), "^[0-9]{17,20}$"))
                return "Client ID Discorda powinien zawierać 17–20 cyfr.";
            if (vibepollo.Checked && createVibepolloToken.Checked &&
                (String.IsNullOrWhiteSpace(vibepolloAdmin.Text) || String.IsNullOrWhiteSpace(vibepolloPassword.Text)))
                return "Automatyczne utworzenie tokenu wymaga loginu i hasła administratora Vibepollo.";
            if (vibepollo.Checked && !createVibepolloToken.Checked && !hasVibepollo && String.IsNullOrWhiteSpace(vibepolloToken.Text))
                return "Podaj istniejący token Vibepollo albo wybierz automatyczne utworzenie tokenu.";
            if (playnite.Checked)
            {
                string path = playnitePath.Text.Trim();
                if (!File.Exists(Path.Combine(path, "Playnite.FullscreenApp.exe"))) return "Nie znaleziono Playnite w wybranym katalogu.";
                if (!File.Exists(Path.Combine(path, "Extensions", "SunshinePlaynite", "SunshinePlaynite.psm1"))) return "Nie znaleziono rozszerzenia Sunshine Playnite Connector.";
            }
            return null;
        }

        private async Task InstallAsync()
        {
            string temporary = Path.Combine(Path.GetTempPath(), "MoonWakerHost-" + Guid.NewGuid().ToString("N"));
            try
            {
                AppendLog("Rozpakowuję bezpieczny pakiet instalacyjny…");
                Directory.CreateDirectory(temporary);
                using (Stream resource = Assembly.GetExecutingAssembly().GetManifestResourceStream("MoonWakerHost.Payload"))
                {
                    if (resource == null) throw new InvalidOperationException("Brak osadzonego pakietu instalacyjnego.");
                    string archive = Path.Combine(temporary, "payload.zip");
                    using (FileStream output = File.Create(archive)) resource.CopyTo(output);
                    ZipFile.ExtractToDirectory(archive, temporary);
                    File.Delete(archive);
                }
                string script = Path.Combine(temporary, "host-services", "install", "Install-MoonWakerHostBundle.ps1");
                ProcessStartInfo info = new ProcessStartInfo("powershell.exe");
                info.UseShellExecute = false;
                info.CreateNoWindow = true;
                info.RedirectStandardOutput = true;
                info.RedirectStandardError = true;
                info.StandardOutputEncoding = Encoding.UTF8;
                info.StandardErrorEncoding = Encoding.UTF8;
                info.Arguments = "-NoProfile -ExecutionPolicy Bypass -File " + Quote(script) +
                    " -ProfileId " + Quote(profileId.Text.Trim()) + " -ProfileName " + Quote(profileName.Text.Trim()) +
                    (discord.Checked ? "" : " -SkipDiscord") + (vibepollo.Checked ? "" : " -SkipVibepollo") +
                    (playnite.Checked ? " -PlayniteDirectory " + Quote(playnitePath.Text.Trim()) : " -SkipPlaynite");
                info.EnvironmentVariables["MOONWAKER_DISCORD_CLIENT_ID"] = discordId.Text.Trim();
                info.EnvironmentVariables["MOONWAKER_DISCORD_CLIENT_SECRET"] = discordSecret.Text;
                info.EnvironmentVariables["MOONWAKER_VIBEPOLLO_URL"] = vibepolloUrl.Text.Trim();
                info.EnvironmentVariables["MOONWAKER_VIBEPOLLO_TOKEN"] = vibepolloToken.Text;
                info.EnvironmentVariables["MOONWAKER_VIBEPOLLO_CREATE_TOKEN"] = createVibepolloToken.Checked ? "1" : "0";
                info.EnvironmentVariables["MOONWAKER_VIBEPOLLO_ADMIN_USERNAME"] = vibepolloAdmin.Text.Trim();
                info.EnvironmentVariables["MOONWAKER_VIBEPOLLO_ADMIN_PASSWORD"] = vibepolloPassword.Text;
                discordSecret.Clear();
                vibepolloToken.Clear();
                vibepolloPassword.Clear();

                using (Process process = new Process())
                {
                    process.StartInfo = info;
                    process.OutputDataReceived += delegate(object s, DataReceivedEventArgs e) { if (e.Data != null) AppendLog(e.Data); };
                    process.ErrorDataReceived += delegate(object s, DataReceivedEventArgs e) { if (e.Data != null) AppendLog("BŁĄD: " + e.Data); };
                    process.Start();
                    process.BeginOutputReadLine();
                    process.BeginErrorReadLine();
                    await Task.Run(delegate { process.WaitForExit(); });
                    if (process.ExitCode != 0) throw new InvalidOperationException("Instalacja nie powiodła się. Szczegóły znajdują się powyżej.");
                }
                progress.Style = ProgressBarStyle.Continuous;
                progress.Value = 100;
                AppendLog("");
                AppendLog("Gotowe. Uruchom ponownie komputer, aby załadować wszystkie integracje.");
                next.Enabled = true;
                MessageBox.Show(this, "Gateway, Bridge'e i profil zostały zainstalowane. Po restarcie Windows uruchom Playnite na tym samym koncie.", "MoonWaker — gotowe", MessageBoxButtons.OK, MessageBoxIcon.Information);
            }
            catch (Exception ex)
            {
                progress.Style = ProgressBarStyle.Continuous;
                progress.Value = 0;
                AppendLog("BŁĄD: " + ex.Message);
                next.Enabled = true;
                MessageBox.Show(this, ex.Message, "MoonWaker — błąd instalacji", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
            finally
            {
                try { if (Directory.Exists(temporary)) Directory.Delete(temporary, true); } catch { }
            }
        }

        private void AppendLog(string text)
        {
            if (InvokeRequired) { BeginInvoke(new Action<string>(AppendLog), text); return; }
            log.AppendText(text + Environment.NewLine);
            log.SelectionStart = log.TextLength;
            log.ScrollToCaret();
        }

        private void AddHeading(string heading, string subtitle)
        {
            Label h = MakeLabel(heading, 20F, FontStyle.Bold);
            h.SetBounds(30, 24, 780, 38);
            content.Controls.Add(h);
            Label s = MakeLabel(subtitle, 9.5F, FontStyle.Regular);
            s.ForeColor = Color.FromArgb(170, 176, 197);
            s.SetBounds(31, 64, 780, 30);
            content.Controls.Add(s);
        }

        private void AddField(string label, TextBox box, int top, string hint)
        {
            Label l = MakeLabel(label, 9F, FontStyle.Bold);
            l.SetBounds(34, top, 250, 24);
            content.Controls.Add(l);
            ConfigureTextBox(box, 34, top + 27, 770, false);
            Label h = MakeLabel(hint, 8.5F, FontStyle.Regular);
            h.ForeColor = Color.FromArgb(145, 151, 171);
            h.SetBounds(34, top + 65, 770, 23);
            content.Controls.Add(h);
        }

        private void AddSmallLabel(string text, int left, int top, int width)
        {
            Label label = MakeLabel(text, 8F, FontStyle.Regular);
            label.ForeColor = Color.FromArgb(155, 161, 180);
            label.SetBounds(left, top, width, 18);
            content.Controls.Add(label);
        }

        private void ConfigureCheckBox(CheckBox box, string text, int left, int top)
        {
            box.Text = text;
            box.ForeColor = Color.White;
            box.BackColor = panelColor;
            box.SetBounds(left, top, 190, 34);
            content.Controls.Add(box);
        }

        private void ConfigureTextBox(TextBox box, int left, int top, int width, bool password)
        {
            box.BackColor = Color.FromArgb(20, 24, 33);
            box.ForeColor = Color.White;
            box.BorderStyle = BorderStyle.FixedSingle;
            box.UseSystemPasswordChar = password;
            box.SetBounds(left, top, width, 34);
            content.Controls.Add(box);
        }

        private Label MakeLabel(string text, float size, FontStyle style)
        {
            Label label = new Label();
            label.Text = text;
            label.Font = new Font("Segoe UI", size, style);
            label.ForeColor = Color.White;
            label.BackColor = Color.Transparent;
            return label;
        }

        private void StyleButton(Button button, bool primary)
        {
            button.FlatStyle = FlatStyle.Flat;
            button.FlatAppearance.BorderSize = primary ? 0 : 1;
            button.FlatAppearance.BorderColor = Color.FromArgb(80, 87, 110);
            button.BackColor = primary ? accent : panelColor;
            button.ForeColor = Color.White;
            button.Cursor = Cursors.Hand;
        }

        private static string Quote(string value)
        {
            return "\"" + value.Replace("\"", "\\\"") + "\"";
        }

        private static string FindPlaynite()
        {
            string[] processNames = { "Playnite.DesktopApp", "Playnite.FullscreenApp" };
            foreach (string name in processNames)
            {
                try
                {
                    Process[] found = Process.GetProcessesByName(name);
                    if (found.Length > 0 && found[0].MainModule != null) return Path.GetDirectoryName(found[0].MainModule.FileName);
                }
                catch { }
            }
            string[] candidates = {
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Playnite"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Playnite"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "Playnite")
            };
            foreach (string candidate in candidates)
                if (File.Exists(Path.Combine(candidate, "Playnite.FullscreenApp.exe"))) return candidate;
            return "";
        }

        private static bool HasMachineDiscordApplication()
        {
            string root = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData),
                "MoonWakerHost");
            return File.Exists(Path.Combine(root, "discord-app.json")) &&
                File.Exists(Path.Combine(root, "discord-app-secret.dpapi"));
        }
    }
}
